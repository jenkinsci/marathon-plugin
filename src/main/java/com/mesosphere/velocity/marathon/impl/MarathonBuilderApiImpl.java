package com.mesosphere.velocity.marathon.impl;

import com.cloudbees.plugins.credentials.Credentials;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileInvalidException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileMissingException;
import com.mesosphere.velocity.marathon.interfaces.AppConfig;
import com.mesosphere.velocity.marathon.interfaces.MarathonApi;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import com.mesosphere.velocity.marathon.util.MarathonBuilderUtils;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import mesosphere.marathon.client.utils.MarathonException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Simple Marathon Deployment that just utilizes a JSON file
 *
 * @author luketornquist
 * @since 1/21/17
 */
public class MarathonBuilderApiImpl extends MarathonBuilder {
    private static final Logger LOGGER = Logger.getLogger(MarathonBuilderApiImpl.class.getName());

    private boolean forceUpdate;
    private JSONObject json;
    private EnvVars envVars;
    private FilePath workspace;

    public MarathonBuilderApiImpl() {
        this(null, null, false);
    }

    public MarathonBuilderApiImpl(String url, String credentialId, boolean forceUpdate) {
        this.envVars = new EnvVars();

        setURLFromConfig(url);
        setCredentialsId(credentialId);
        this.forceUpdate = forceUpdate;
    }

    @Override
    public MarathonBuilder read(final String filename) throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException {
        final String   realFilename = StringUtils.isNotBlank(filename) ? filename : MarathonBuilderUtils.MARATHON_JSON;
        final FilePath marathonFile = workspace.child(realFilename);

        if (!marathonFile.exists()) {
            throw new MarathonFileMissingException(realFilename);
        } else if (marathonFile.isDirectory()) {
            throw new MarathonFileInvalidException("File '" + realFilename + "' is a directory.");
        }

        final String content = marathonFile.readToString();
        // TODO: Validate the JSON?
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
    public MarathonBuilder setConfig(AppConfig config) {
        setURLFromConfig(config.getUrl());
        setCredentialsId(config.getCredentialsId());
        this.forceUpdate = config.getForceUpdate();
        return this;
    }

    @Override
    public MarathonBuilder setWorkspace(final FilePath ws) {
        this.workspace = ws;
        return this;
    }

    @Override
    public MarathonBuilder build() {
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
     * Construct a MarathonAPI based on the provided credentialsId and execute an update for ths configuration's
     * Marathon application.
     *
     * @param credentialsId A string ID for a credential within Jenkin's Credential store
     * @throws MarathonException thrown if the Marathon service has an error
     */
    @Override
    protected void doUpdate(final String credentialsId) throws MarathonException {
        final Credentials credentials = MarathonBuilderUtils.getJenkinsCredentials(credentialsId, Credentials.class);
        final String appId = json.getString(MarathonBuilderUtils.JSON_ID_FIELD);

        MarathonApi marathonApi = new MarathonApiImpl(getURL(), credentials);
        marathonApi.update(appId, this.json.toString(), this.forceUpdate);
    }

    private void setURLFromConfig(final String url) {
        if (url != null) {
            setURL(Util.replaceMacro(url, envVars));
        }
    }
}
