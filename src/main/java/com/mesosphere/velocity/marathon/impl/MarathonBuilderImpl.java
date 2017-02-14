package com.mesosphere.velocity.marathon.impl;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.mesosphere.velocity.marathon.auth.TokenAuthProvider;
import com.mesosphere.velocity.marathon.exceptions.AuthenticationException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileInvalidException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileMissingException;
import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.interfaces.AppConfig;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import com.mesosphere.velocity.marathon.util.MarathonBuilderUtils;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.MarathonClient;
import mesosphere.marathon.client.model.v2.App;
import mesosphere.marathon.client.utils.MarathonException;
import mesosphere.marathon.client.utils.ModelUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class MarathonBuilderImpl extends MarathonBuilder {
    private static final Logger LOGGER = Logger.getLogger(MarathonBuilderImpl.class.getName());
    private AppConfig  config;
    private JSONObject json;
    private App        app;
    private EnvVars    envVars;
    private FilePath   workspace;

    public MarathonBuilderImpl() {
        this(null);
    }

    public MarathonBuilderImpl(final AppConfig config) {
        this.config = config;
        this.envVars = new EnvVars();

        setURLFromConfig();
    }

    public List<MarathonUri> getUris() {
        return config.getUris();
    }

    public List<MarathonLabel> getLabels() {
        return config.getLabels();
    }

    public String getAppid() {
        return config.getAppId();
    }

    public String getDocker() {
        return config.getDocker();
    }

    public boolean getDockerForcePull() {
        return config.getDockerForcePull();
    }

    /**
     * Perform the actual update call to Marathon. If a 401 (Unauthenticated) response is received,
     * this will try to retrieve a new token from DC/OS using JWT credentials.
     *
     * @return this Marathon builder
     * @throws MarathonException       If Marathon does not return a 20x OK response
     * @throws AuthenticationException If an authentication provider was used and encountered a problem.
     */
    @Override
    public MarathonBuilder update() throws MarathonException, AuthenticationException {
        if (app != null) {
            try {
                doUpdate(config.getCredentialsId());
            } catch (MarathonException marathonException) {
                LOGGER.warning("Marathon Exception: " + marathonException.getMessage());

                // 401 results may be possible to resolve, others not so much
                if (marathonException.getStatus() != 401) throw marathonException;
                LOGGER.fine("Received 401 when updating Marathon application.");

                final StringCredentials tokenCredentials = MarathonBuilderUtils.getTokenCredentials(config.getCredentialsId());
                if (tokenCredentials == null) {
                    LOGGER.warning("Unauthorized (401) and service account credentials are not filled in.");
                    throw marathonException;
                }

                // check if service account credentials were configured
                // try to determine correct provider and update token
                // (there is only one provider thus far, so this is simple)
                boolean                 updatedToken = false;
                final TokenAuthProvider provider     = TokenAuthProvider.getTokenAuthProvider(TokenAuthProvider.Providers.DCOS, tokenCredentials);
                if (provider != null) {
                    updatedToken = provider.updateTokenCredentials(tokenCredentials);
                }

                // use the new token if it was updated
                if (updatedToken) {
                    LOGGER.info("Token was successfully updated.");
                    doUpdate(config.getCredentialsId());
                }
            }
        }

        return this;
    }

    @Override
    public MarathonBuilder read(final String filename) throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException {
        final String   realFilename = filename != null ? filename : MarathonBuilderUtils.MARATHON_JSON;
        final FilePath marathonFile = workspace.child(realFilename);

        if (!marathonFile.exists()) {
            throw new MarathonFileMissingException(realFilename);
        } else if (marathonFile.isDirectory()) {
            throw new MarathonFileInvalidException("File '" + realFilename + "' is a directory.");
        }

        final String content = marathonFile.readToString();
        this.json = JSONObject.fromObject(content);
        return this;
    }

    @Override
    public MarathonBuilder read() throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException {
        return read(null);
    }

    @Override
    public JSONObject getJson() {
        return this.json;
    }

    @Override
    public MarathonBuilder setJson(final JSONObject json) {
        this.json = json;
        return this;
    }

    @Override
    public MarathonBuilder setEnvVars(final EnvVars vars) {
        this.envVars = vars;
        return this;
    }

    @Override
    public MarathonBuilder setConfig(final AppConfig config) {
        this.config = config;
        return this;
    }

    @Override
    public MarathonBuilder setWorkspace(final FilePath ws) {
        this.workspace = ws;
        return this;
    }

    @Override
    public MarathonBuilder build() {
        setURLFromConfig();

        setId();
        setDockerImage();
        setUris();
        setLabels();

        this.app = ModelUtils.GSON.fromJson(json.toString(), App.class);
        return this;
    }

    @Override
    public MarathonBuilder toFile(final String filename) throws InterruptedException, IOException, MarathonFileInvalidException {
        final String   realFilename     = filename != null ? filename : MarathonBuilderUtils.MARATHON_RENDERED_JSON;
        final FilePath renderedFilepath = workspace.child(Util.replaceMacro(realFilename, envVars));
        if (renderedFilepath.exists() && renderedFilepath.isDirectory())
            throw new MarathonFileInvalidException("File '" + realFilename + "' is a directory; not overwriting.");

        renderedFilepath.write(json.toString(), null);
        return this;
    }

    @Override
    public MarathonBuilder toFile() throws InterruptedException, IOException, MarathonFileInvalidException {
        return toFile(null);
    }

    /**
     * Construct a Marathon client based on the provided credentialsId and execute an update for ths configuration's
     * Marathon application.
     *
     * @param credentialsId A string ID for a credential within Jenkin's Credential store
     * @throws MarathonException thrown if the Marathon service has an error
     */
    private void doUpdate(final String credentialsId) throws MarathonException {
        final Credentials credentials = MarathonBuilderUtils.getJenkinsCredentials(credentialsId, Credentials.class);

        Marathon client;

        if (credentials instanceof UsernamePasswordCredentials) {
            client = getMarathonClient((UsernamePasswordCredentials) credentials);
        } else if (credentials instanceof StringCredentials) {
            client = getMarathonClient((StringCredentials) credentials);
        } else {
            client = getMarathonClient();
        }

        if (client != null) {
            client.updateApp(app.getId(), app, config.getForceUpdate());
        }
    }

    /**
     * Get a Marathon client with basic auth using the username and password within the provided credentials.
     *
     * @param credentials Username and password credentials
     * @return Marathon client with basic authentication filled in
     */
    private Marathon getMarathonClient(UsernamePasswordCredentials credentials) {
        return MarathonClient
                .getInstanceWithBasicAuth(getURL(), credentials.getUsername(), credentials.getPassword().getPlainText());
    }

    /**
     * Get a Marathon client with Authorization headers using the token within provided credentials. If the content of
     * credentials is JSON, this will use the "jenkins_token" field; if the content is just a string, that will be
     * used as the token value.
     *
     * @param credentials String credentials
     * @return Marathon client with token in auth header
     */
    private Marathon getMarathonClient(StringCredentials credentials) {
        String token;

        try {
            final JSONObject json = JSONObject.fromObject(credentials.getSecret().getPlainText());
            if (json.has("jenkins_token")) {
                token = json.getString("jenkins_token");
            } else {
                token = "";
            }
        } catch (JSONException jse) {
            token = credentials.getSecret().getPlainText();
        }

        if (StringUtils.isNotEmpty(token)) {
            return MarathonClient
                    .getInstanceWithTokenAuth(getURL(), token);
        }

        return getMarathonClient();
    }

    /**
     * Get a default Marathon client. This does not include any authentication headers.
     *
     * @return Marathon client without authentication mechanisms
     */
    private Marathon getMarathonClient() {
        return MarathonClient.getInstance(getURL());
    }

    private JSONObject setId() {
        if (config.getAppId() != null && config.getAppId().trim().length() > 0)
            json.put(MarathonBuilderUtils.JSON_ID_FIELD, Util.replaceMacro(config.getAppId(), envVars));

        return json;
    }

    private void setURLFromConfig() {
        if (config.getUrl() != null) setURL(Util.replaceMacro(config.getUrl(), envVars));
    }

    private JSONObject setDockerImage() {
        if (config.getDocker() != null && config.getDocker().trim().length() > 0) {
            // get container -> docker -> image
            if (!json.has(MarathonBuilderUtils.JSON_CONTAINER_FIELD)) {
                json.element(MarathonBuilderUtils.JSON_CONTAINER_FIELD,
                        JSONObject.fromObject(MarathonBuilderUtils.JSON_EMPTY_CONTAINER));
            }

            final JSONObject container = json.getJSONObject(MarathonBuilderUtils.JSON_CONTAINER_FIELD);

            if (!container.has(MarathonBuilderUtils.JSON_DOCKER_FIELD)) {
                container.element(MarathonBuilderUtils.JSON_DOCKER_FIELD, new JSONObject());
            }

            container.getJSONObject(MarathonBuilderUtils.JSON_DOCKER_FIELD)
                    .element(MarathonBuilderUtils.JSON_DOCKER_IMAGE_FIELD,
                            Util.replaceMacro(config.getDocker(), envVars));

            container.getJSONObject(MarathonBuilderUtils.JSON_DOCKER_FIELD)
                    .element(MarathonBuilderUtils.JSON_DOCKER_IMAGE_FORCE_PULL,
                            config.getDockerForcePull());

        }

        return json;
    }

    /**
     * Set the root "uris" JSON array with the URIs configured within
     * the Jenkins UI. This handles transforming Environment Variables
     * to their actual values.
     */
    private JSONObject setUris() {
        if (config.getUris().size() > 0) {
            final boolean hasUris = json.get(MarathonBuilderUtils.JSON_URI_FIELD) instanceof JSONArray;

            // create the "uris" field if one is not there or it is of the wrong type.
            if (!hasUris)
                json.element(MarathonBuilderUtils.JSON_URI_FIELD, new JSONArray());

            for (MarathonUri uri : config.getUris()) {
                json.accumulate(MarathonBuilderUtils.JSON_URI_FIELD, Util.replaceMacro(uri.getUri(), envVars));
            }
        }

        return json;
    }

    private JSONObject setLabels() {
        if (!json.has("labels"))
            json.element("labels", new JSONObject());

        final JSONObject labelObject = json.getJSONObject("labels");
        for (MarathonLabel label : config.getLabels()) {
            labelObject.element(Util.replaceMacro(label.getName(), envVars),
                    Util.replaceMacro(label.getValue(), envVars));
        }

        return json;
    }
}
