package com.mesosphere.velocity.marathon.impl;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.mesosphere.velocity.marathon.auth.TokenAuthProvider;
import com.mesosphere.velocity.marathon.exceptions.AuthenticationException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileInvalidException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileMissingException;
import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.fields.MarathonVars;
import com.mesosphere.velocity.marathon.interfaces.AppConfig;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import com.mesosphere.velocity.marathon.util.MarathonBuilderUtils;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.MarathonClient;
import mesosphere.marathon.client.MarathonException;
import mesosphere.marathon.client.model.v2.Container;
import mesosphere.marathon.client.model.v2.Docker;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class MarathonBuilderImpl extends MarathonBuilder {
    private static final Logger LOGGER = Logger.getLogger(MarathonBuilderImpl.class.getName());
    private AppConfig  config;
    private JSONObject json;
    private EnvVars    envVars;
    private FilePath   workspace;

    public MarathonBuilderImpl() {
        this(null);
    }

    public MarathonBuilderImpl(AppConfig config) {
        this.config = config;
        this.envVars = new EnvVars();

        setURLFromConfig();
    }

    public List<MarathonUri> getUris() {
        return this.config.getUris();
    }

    public List<MarathonLabel> getLabels() {
        return this.config.getLabels();
    }

    public String getAppid() {
        return this.config.getAppId();
    }

    public String getDocker() {
        return this.config.getDocker();
    }

    public boolean getDockerForcePull() {
        return this.config.getDockerForcePull();
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
        if (getApp() != null) {
            try {
                doUpdate(this.config.getCredentialsId());
            } catch (MarathonException marathonException) {
                LOGGER.warning("Marathon Exception: " + marathonException.getMessage());

                // 401 results may be possible to resolve, others not so much
                if (marathonException.getStatus() != 401) {
                    throw marathonException;
                }
                LOGGER.fine("Received 401 when updating Marathon application.");

                StringCredentials tokenCredentials = MarathonBuilderUtils.getTokenCredentials(this.config.getCredentialsId());
                if (tokenCredentials == null) {
                    LOGGER.warning("Unauthorized (401) and service account credentials are not filled in.");
                    throw marathonException;
                }

                // check if service account credentials were configured
                // try to determine correct provider and update token
                // (there is only one provider thus far, so this is simple)
                boolean                 updatedToken = false;
                TokenAuthProvider provider = TokenAuthProvider.getTokenAuthProvider(TokenAuthProvider.Providers.DCOS, tokenCredentials);
                if (provider != null) {
                    updatedToken = provider.updateTokenCredentials(tokenCredentials);
                }

                // use the new token if it was updated
                if (updatedToken) {
                    LOGGER.info("Token was successfully updated.");
                    doUpdate(this.config.getCredentialsId());
                }
            }
        }

        return this;
    }

    @Override
    public MarathonBuilder read(String filename) throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException {
        String realFilename = filename != null ? filename : MarathonBuilderUtils.MARATHON_JSON;
        FilePath marathonFile = this.workspace.child(realFilename);

        if (!marathonFile.exists()) {
            throw new MarathonFileMissingException(realFilename);
        } else if (marathonFile.isDirectory()) {
            throw new MarathonFileInvalidException("File '" + realFilename + "' is a directory.");
        }

        String content = marathonFile.readToString();
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
    public MarathonBuilder setJson(JSONObject json) {
        this.json = json;
        return this;
    }

    @Override
    public MarathonBuilder setEnvVars(EnvVars vars) {
        this.envVars = vars;
        return this;
    }

    @Override
    public MarathonBuilder setConfig(AppConfig config) {
        this.config = config;
        return this;
    }

    @Override
    public MarathonBuilder setWorkspace(FilePath ws) {
        this.workspace = ws;
        return this;
    }

    @Override
    public MarathonBuilder build() {
        setURLFromConfig();
        setAppFromJson(this.json);

        setId();
        setDockerImage();
        setUris();
        setLabels();
        setEnv();

        return this;
    }

    @Override
    public MarathonBuilder toFile(String filename) throws InterruptedException, IOException, MarathonFileInvalidException {
        String realFilename = filename != null ? filename : MarathonBuilderUtils.MARATHON_RENDERED_JSON;
        FilePath renderedFilepath = this.workspace.child(Util.replaceMacro(realFilename, this.envVars));
        if (renderedFilepath.exists() && renderedFilepath.isDirectory()) {
            throw new MarathonFileInvalidException("File '" + realFilename + "' is a directory; not overwriting.");
        }

        renderedFilepath.write(this.json.toString(), null);
        return this;
    }

    @Override
    public MarathonBuilder toFile() throws InterruptedException, IOException, MarathonFileInvalidException {
        return toFile(null);
    }

    /**
     * Construct a Marathon client based on the provided credentialsId and execute an update for the configuration's
     * Marathon application.
     *
     * @param credentialsId A string ID for a credential within Jenkin's Credential store
     * @throws MarathonException thrown if the Marathon service has an error
     */
    private void doUpdate(String credentialsId) throws MarathonException {
        Credentials credentials = MarathonBuilderUtils.getJenkinsCredentials(credentialsId, Credentials.class);

        Marathon client;

        if (credentials instanceof UsernamePasswordCredentials) {
            client = getMarathonClient((UsernamePasswordCredentials) credentials);
        } else if (credentials instanceof StringCredentials) {
            client = getMarathonClient((StringCredentials) credentials);
        } else {
            client = getMarathonClient();
        }

        if (client != null) {
            long timeout = 5 * 60 * 1000L; // timeout after 5 min
            boolean success = new MarathonApiImpl().update(client, getApp(), this.config.getForceUpdate(), timeout);
            if (!success) {
                throw new MarathonException(408, String.format("The deployment timed out after %dms", timeout));
            }
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
            JSONObject json = JSONObject.fromObject(credentials.getSecret().getPlainText());
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

    private void setId() {
        if (this.config.getAppId() != null && this.config.getAppId().trim().length() > 0) {
            String appId = Util.replaceMacro(this.config.getAppId(), this.envVars);
            if (appId != null && appId.trim().length() > 0) {
                getApp().setId(appId);
            }
        }
    }

    private void setURLFromConfig() {
        if (this.config.getUrl() != null) {
            setURL(Util.replaceMacro(this.config.getUrl(), this.envVars));
        }
    }

    private void setDockerImage() {
        if (this.config.getDocker() != null && this.config.getDocker().trim().length() > 0) {
            String imageName = Util.replaceMacro(this.config.getDocker(), this.envVars);

            if (imageName == null || imageName.trim().length() == 0) {
                return;
            }

            if (getApp().getContainer() == null) {
                getApp().setContainer(new Container());
            }

            if (getApp().getContainer().getDocker() == null) {
                getApp().getContainer().setDocker(new Docker());
            }

            getApp().getContainer().setType("DOCKER");
            getApp().getContainer().getDocker().setImage(imageName);
            getApp().getContainer().getDocker().setForcePullImage(this.config.getDockerForcePull());
        }
    }

    /**
     * Set the root "uris" JSON array with the URIs configured within
     * the Jenkins UI. This handles transforming Environment Variables
     * to their actual values.
     */
    private void setUris() {
        if (CollectionUtils.isNotEmpty(this.config.getUris())) {
            for (MarathonUri uri : this.config.getUris()) {
                String replacedUri = Util.replaceMacro(uri.getUri(), this.envVars);
                getApp().addUri(replacedUri);
            }
        }
    }

    private void setLabels() {
        if (CollectionUtils.isNotEmpty(this.config.getLabels())) {
            for (MarathonLabel label : this.config.getLabels()) {
                String labelName = Util.replaceMacro(label.getName(), this.envVars);
                String labelValue = Util.replaceMacro(label.getValue(), this.envVars);

                getApp().addLabel(labelName, labelValue);
            }
        }
    }


    private void setEnv() {
        if (CollectionUtils.isNotEmpty(this.config.getEnv())) {
            Map<String, Object> envsToAdd = new HashMap<>(this.config.getEnv().size());
            for (MarathonVars var : this.config.getEnv()) {
                envsToAdd.put(
                        Util.replaceMacro(var.getName(), this.envVars),
                        Util.replaceMacro(var.getValue(), this.envVars));
            }

            if (MapUtils.isEmpty(getApp().getEnv())) {
                getApp().setEnv(envsToAdd);
            } else {
                getApp().getEnv().putAll(envsToAdd);
            }
        }
    }
}
