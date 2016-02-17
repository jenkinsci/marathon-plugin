package org.jenkinsci.plugins.marathon.impl;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.MarathonClient;
import mesosphere.marathon.client.model.v2.App;
import mesosphere.marathon.client.utils.MarathonException;
import mesosphere.marathon.client.utils.ModelUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.marathon.fields.MarathonLabel;
import org.jenkinsci.plugins.marathon.fields.MarathonUri;
import org.jenkinsci.plugins.marathon.interfaces.AppConfig;
import org.jenkinsci.plugins.marathon.interfaces.MarathonBuilder;
import org.jenkinsci.plugins.marathon.util.MarathonBuilderUtils;

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
    }

    public String getUrl() {
        return config.getUrl();
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
    public MarathonBuilder create() {
        return this;
    }

    @Override
    public MarathonBuilder update() throws MarathonException {
        if (app != null) {
            final Marathon marathon = MarathonClient.getInstance(config.getUrl());
            marathon.updateApp(app.getId(), app, false);   // uses PUT
        }
        return this;
    }

    @Override
    public MarathonBuilder delete() {
        return this;
    }

    @Override
    public MarathonBuilder read(final String filename) throws IOException, InterruptedException {
        final FilePath marathonFile = workspace.child(filename != null ? filename : MarathonBuilderUtils.MARATHON_JSON);

        if (!marathonFile.exists()) {
            final String errorMsg = "File '" + marathonFile.getName() + "' does not exist.";
            LOGGER.warning(errorMsg);
            throw new IOException(errorMsg);
        } else if (marathonFile.isDirectory()) {
            final String errorMsg = "File '" + marathonFile.getName() + "' is a directory.";
            LOGGER.warning(errorMsg);
            throw new IOException(errorMsg);
        }

        final String content = marathonFile.readToString();
        this.json = JSONObject.fromObject(content);
        LOGGER.fine("Read contents of '" + marathonFile.getName() + "'");
        return this;
    }

    @Override
    public MarathonBuilder read() throws IOException, InterruptedException {
        return read(null);
    }

    @Override
    public MarathonBuilder setJson(final JSONObject json) {
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
        LOGGER.fine("Built Marathon app from JSON");
        return this;
    }

    @Override
    public MarathonBuilder toFile(final String filename) throws InterruptedException {
        final FilePath renderedFilepath = workspace.child(
                Util.replaceMacro(filename != null ? filename : MarathonBuilderUtils.MARATHON_RENDERED_JSON, envVars));
        try {
            if (renderedFilepath.exists() && renderedFilepath.isDirectory())
                throw new IOException("File '" + filename + "' is a directory; not overwriting.");

            renderedFilepath.write(json.toString(), null);
            LOGGER.fine("Wrote JSON to '" + renderedFilepath + "'");
        } catch (IOException e) {
            LOGGER.warning("Exception encountered when writing rendered JSON to '" + renderedFilepath + "'");
            LOGGER.warning(e.getLocalizedMessage());
        }
        return this;
    }

    @Override
    public MarathonBuilder toFile() throws InterruptedException {
        return toFile(null);
    }

    @Override
    public String toString() {
        return app.toString();
    }

    private JSONObject setId() {
        if (config.getAppId() != null && config.getAppId().trim().length() > 0)
            json.put(MarathonBuilderUtils.JSON_ID_FIELD, Util.replaceMacro(config.getAppId(), envVars));

        return json;
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
        }

        return json;
    }

    /**
     * Set the root "uris" JSON array with the URIs configured within
     * the Jenkins UI. This handles transforming Environment Variables
     * to their actual values.
     */
    private JSONObject setUris() {
        json.element(MarathonBuilderUtils.JSON_URI_FIELD, new JSONArray());
        for (MarathonUri uri : config.getUris()) {
            json.accumulate(MarathonBuilderUtils.JSON_URI_FIELD, Util.replaceMacro(uri.getUri(), envVars));
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
