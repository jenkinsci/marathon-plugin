package com.mesosphere.velocity.marathon.impl;

import com.mesosphere.velocity.marathon.exceptions.MarathonFileInvalidException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileMissingException;
import com.mesosphere.velocity.marathon.fields.DeployConfig;
import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.interfaces.AppConfig;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import com.mesosphere.velocity.marathon.util.MarathonBuilderUtils;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import mesosphere.marathon.client.model.v2.App;
import mesosphere.marathon.client.utils.MarathonException;
import mesosphere.marathon.client.utils.ModelUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class MarathonBuilderImpl extends MarathonBuilder {
    private static final Logger LOGGER = Logger.getLogger(MarathonBuilderImpl.class.getName());
    private AppConfig       config;
    private JSONObject      json;
    private App             app;
    private FilePath        workspace;

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
     * @throws MarathonException thrown if the Marathon service has an error
     */
    @Override
    protected void doUpdate() throws MarathonException {
        if (getMarathonClient() != null) {
            getMarathonClient().updateApp(app.getId(), app, config.getForceUpdate());
        }
    }


    private JSONObject setId() {
        if (config.getAppId() != null && config.getAppId().trim().length() > 0)
            json.put(MarathonBuilderUtils.JSON_ID_FIELD, Util.replaceMacro(config.getAppId(), getEnvVars()));

        return json;
    }

    private JSONObject setDockerImage() {
        if (config.getDocker() != null && config.getDocker().trim().length() > 0) {
            //LOGGER.info("Docker");
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
                            Util.replaceMacro(config.getDocker(), getEnvVars()));

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
                json.accumulate(MarathonBuilderUtils.JSON_URI_FIELD, Util.replaceMacro(uri.getUri(), getEnvVars()));
            }
        }

        return json;
    }

    private JSONObject setLabels() {
        if (!json.has("labels"))
            json.element("labels", new JSONObject());

        final JSONObject labelObject = json.getJSONObject("labels");
        for (MarathonLabel label : config.getLabels()) {
            labelObject.element(Util.replaceMacro(label.getName(), getEnvVars()),
                    Util.replaceMacro(label.getValue(), getEnvVars()));
        }

        return json;
    }
}
