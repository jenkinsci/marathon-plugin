package com.mesosphere.velocity.marathon.impl;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileInvalidException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileMissingException;
import com.mesosphere.velocity.marathon.fields.DeployConfig;
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
import mesosphere.marathon.client.model.v2.App;
import mesosphere.marathon.client.model.v2.Container;
import mesosphere.marathon.client.model.v2.Docker;
import mesosphere.marathon.client.utils.MarathonException;
import mesosphere.marathon.client.utils.ModelUtils;
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
    private App app;
    private JSONObject json;
    private FilePath   workspace;

    public MarathonBuilderImpl() {
        this(new EnvVars(), null);
    }

    public MarathonBuilderImpl(final EnvVars envVars, final AppConfig config) {
        super(envVars, config == null ? null : config.getUrl(),
              config == null ? null : config.getCredentialsId());
        this.config = config;
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

    public App getApp() {
        return app;
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
    public MarathonBuilder setConfig(final AppConfig config) {
        this.config = config;
        return this;
    }

    @Override
    public MarathonBuilder setConfig(DeployConfig config) {
        LOGGER.warning("MarathonBuilderImpl does not currently support 'DeployConfig'-based configuration");
        return this;
    }

    @Override
    public MarathonBuilder setWorkspace(final FilePath ws) {
        this.workspace = ws;
        return this;
    }

    @Override
    public MarathonBuilder build() {
        this.app = ModelUtils.GSON.fromJson(json.toString(), App.class);

        setId();
        setDockerImage();
        setUris();
        setLabels();
        setEnv();

        return this;
    }

    @Override
    public MarathonBuilder toFile(final String filename) throws InterruptedException, IOException, MarathonFileInvalidException {
        final String   realFilename     = filename != null ? filename : MarathonBuilderUtils.MARATHON_RENDERED_JSON;
        final FilePath renderedFilepath = workspace.child(Util.replaceMacro(realFilename, getEnvVars()));
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
    @Override
    protected void doUpdate(final String credentialsId) throws MarathonException {
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
            client.updateApp(this.app.getId(), this.app, config.getForceUpdate());
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

    private void setId() {
        if (config.getAppId() != null && config.getAppId().trim().length() > 0) {
            final String appId = Util.replaceMacro(config.getAppId(), getEnvVars());
            if (appId != null && appId.trim().length() > 0) this.app.setId(appId);
        }
    }

    private void setDockerImage() {
        if (config.getDocker() != null && config.getDocker().trim().length() > 0) {
            final String imageName = Util.replaceMacro(config.getDocker(), getEnvVars());

            if (imageName == null || imageName.trim().length() == 0) {
                return;
            }

            if (this.app.getContainer() == null) {
                this.app.setContainer(new Container());
            }

            if (this.app.getContainer().getDocker() == null) {
                this.app.getContainer().setDocker(new Docker());
            }

            this.app.getContainer().setType("DOCKER");
            this.app.getContainer().getDocker().setImage(imageName);
            this.app.getContainer().getDocker().setForcePullImage(config.getDockerForcePull());
        }
    }

    /**
     * Set the root "uris" JSON array with the URIs configured within
     * the Jenkins UI. This handles transforming Environment Variables
     * to their actual values.
     */
    private void setUris() {
        if (config.getUris() != null && config.getUris().size() > 0) {
            for (MarathonUri uri : config.getUris()) {
                final String replacedUri = Util.replaceMacro(uri.getUri(), getEnvVars());
                this.app.addUri(replacedUri);
            }
        }
    }

    private void setLabels() {
        if (config.getLabels() != null && config.getLabels().size() > 0) {
            for (MarathonLabel label : config.getLabels()) {
                final String labelName  = Util.replaceMacro(label.getName(), getEnvVars());
                final String labelValue = Util.replaceMacro(label.getValue(), getEnvVars());

                this.app.addLabel(labelName, labelValue);
            }
        }
    }


    private JSONObject setEnv() {
        if (!json.has("env"))
            json.element("env", new JSONObject());

        final JSONObject envObject = json.getJSONObject("env");
        for (MarathonVars var : config.getEnv()) {
            envObject.element(Util.replaceMacro(var.getName(), getEnvVars()),
                    Util.replaceMacro(var.getValue(), getEnvVars()));
        }

        return json;
    }
}
