package com.mesosphere.velocity.marathon.impl;

import com.cloudbees.plugins.credentials.Credentials;
import com.mesosphere.velocity.marathon.auth.TokenAuthProvider;
import com.mesosphere.velocity.marathon.exceptions.AuthenticationException;
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
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Simple Marathon Deployment that just utilizes a JSON file
 *
 * @author luketornquist
 * @since 1/21/17
 */
public class SimpleMarathonBuilderImpl extends MarathonBuilder {
    private static final Logger LOGGER = Logger.getLogger(SimpleMarathonBuilderImpl.class.getName());

    private String credentialsId;
    private boolean forceUpdate;
    private JSONObject json;
    private EnvVars envVars;
    private FilePath workspace;

    public SimpleMarathonBuilderImpl() {
        this(null, null, false);
    }

    public SimpleMarathonBuilderImpl(String url, String credentialId, boolean forceUpdate) {
        this.envVars = new EnvVars();

        setURLFromConfig(url);
        this.credentialsId = credentialId;
        this.forceUpdate = forceUpdate;
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
        try {
            doUpdate(this.credentialsId);
        } catch (MarathonException marathonException) {
            LOGGER.warning("Marathon Exception: " + marathonException.getMessage());

            // 401 results may be possible to resolve, others not so much
            if (marathonException.getStatus() != 401) {
                throw marathonException;
            }
            LOGGER.fine("Received 401 when updating Marathon application.");

            final StringCredentials tokenCredentials = MarathonBuilderUtils.getTokenCredentials(this.credentialsId);
            if (tokenCredentials == null) {
                LOGGER.warning("Unauthorized (401) and service account credentials are not filled in.");
                throw marathonException;
            }

            // check if service account credentials were configured
            // try to determine correct provider and update token
            // (there is only one provider thus far, so this is simple)
            boolean updatedToken = false;
            final TokenAuthProvider provider = TokenAuthProvider.getTokenAuthProvider(TokenAuthProvider.Providers.DCOS, tokenCredentials);
            if (provider != null) {
                updatedToken = provider.updateTokenCredentials(tokenCredentials);
            }

            // use the new token if it was updated
            if (updatedToken) {
                LOGGER.info("Token was successfully updated.");
                doUpdate(this.credentialsId);
            }
        }
        return this;
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
        this.credentialsId = config.getCredentialsId();
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
     * Construct a Marathon client based on the provided credentialsId and execute an update for ths configuration's
     * Marathon application.
     *
     * @param credentialsId A string ID for a credential within Jenkin's Credential store
     * @throws MarathonException thrown if the Marathon service has an error
     */
    private void doUpdate(final String credentialsId) throws MarathonException {
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
