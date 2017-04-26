package com.mesosphere.velocity.marathon.interfaces;

import com.mesosphere.velocity.marathon.auth.TokenAuthProvider;
import com.mesosphere.velocity.marathon.exceptions.AuthenticationException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileInvalidException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileMissingException;
import com.mesosphere.velocity.marathon.fields.DeployConfig;
import com.mesosphere.velocity.marathon.impl.MarathonBuilderApiImpl;
import com.mesosphere.velocity.marathon.impl.MarathonBuilderImpl;
import com.mesosphere.velocity.marathon.util.MarathonBuilderUtils;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import mesosphere.marathon.client.utils.MarathonException;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;

/**
 * This builds {@link mesosphere.marathon.client.MarathonClient Marathon Clients} from Jenkins, file system, and JSON pieces.
 * This allows the construction of the final payload as well as sending it to the target Marathon instance.
 */
public abstract class MarathonBuilder {
    private static final Logger LOGGER = Logger.getLogger(MarathonBuilder.class.getName());
    /**
     * Local URL value that may be different than what was passed through config.
     */
    private final String url;

    /**
     * Local Credentials ID
     */
    private final String credentialsId;
    private final EnvVars envVars;
    private PrintStream jenkinsLogger;

    protected MarathonBuilder(final EnvVars envVars, final String url, final String credentialsId) {
        this.envVars = envVars;
        this.url = url == null ? null : Util.replaceMacro(url, envVars);
        this.credentialsId = credentialsId;
    }

    /**
     * Create a new builder instance from config.
     *
     * @param envVars Environment Variables
     * @param config Application configuration
     * @return A new builder
     */
    public static MarathonBuilder getBuilder(final EnvVars envVars, final AppConfig config) {
        return new MarathonBuilderImpl(envVars, config);
    }

    public static MarathonBuilder getBuilder(final EnvVars envVars, final String url, final String credentialId, final boolean injectJenkinsVariables) {
        return new MarathonBuilderApiImpl(envVars, url, credentialId, injectJenkinsVariables);
    }

    public String getURL() {
        return this.url;
    }

    public String getCredentialsId() {
        return this.credentialsId;
    }

    protected EnvVars getEnvVars() {
        return this.envVars;
    }

    /**
     * Set the Jenkins Logger
     *
     * @param logger - Jenkins Logger
     * @return This builder
     */
    public MarathonBuilder setLogger(final PrintStream logger) {
        this.jenkinsLogger = logger;
        return this;
    }
    /**
     * Write text to the build's console log (logger), prefixed with
     * "[Marathon]".
     *
     * @param text   message to log
     */
    protected void log(final String text) {
        if (this.jenkinsLogger != null) {
            this.jenkinsLogger.println("[Marathon] " + text);
        }
    }

    /**
     * Perform the actual Update call to the DCOS server.
     *
     * @param credentialsId - Credentials ID
     * @throws MarathonException on error talking to Marathon service
     * @throws AuthenticationException when authentication with Marathon service fails
     */
    protected abstract void doUpdate(String credentialsId) throws MarathonException, AuthenticationException;

    /**
     * Perform the actual update call to Marathon. If a 401 (Unauthenticated) response is received,
     * this will try to retrieve a new token from DC/OS using JWT credentials.
     *
     * @return This builder
     * @throws MarathonException on error talking to Marathon service
     * @throws AuthenticationException when authentication with Marathon service fails
     */
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

    protected String replaceMacro(String value) {
        if (this.envVars != null && value != null) {
            return Util.replaceMacro(value, this.envVars);
        }
        return value;
    }

    /**
     * Read in filename as JSON.
     *
     * @param filename Path to the JSON file
     * @return This builder
     * @throws IOException on IO issues
     * @throws InterruptedException on complications reading file
     * @throws MarathonFileMissingException when the Marathon config file is missing
     * @throws MarathonFileInvalidException when the Marathon config is not a file
     */
    public abstract MarathonBuilder read(final String filename)
            throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException;

    /**
     * Read in default file (marathon.json) as JSON.
     *
     * @return This builder
     * @throws IOException on IO issues
     * @throws InterruptedException on complications reading file
     * @throws MarathonFileMissingException when the Marathon config file is missing
     * @throws MarathonFileInvalidException when the Marathon config is not a file
     * @see #read(String)
     */
    public abstract MarathonBuilder read()
            throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException;

    public abstract JSONObject getJson();

    /**
     * Set the JSON for this builder to json. This will overwrite the value set by {@link #read(String)}.
     *
     * @param json The JSON value to use for this builder
     * @return This builder
     */
    public abstract MarathonBuilder setJson(final JSONObject json);

    /**
     * Set the Application Configuration to config.
     *
     * @param config application configuration
     * @return This builder
     */
    public abstract MarathonBuilder setConfig(final AppConfig config);

    /**
     * Set the Deployment Configuration to config.
     *
     * @param config - Deployment Configuration
     * @return This builder
     */
    public abstract MarathonBuilder setConfig(final DeployConfig config);

    /**
     * Set the Jenkins workspace to ws.
     *
     * @param ws Jenkins workspace
     * @return This builder
     */
    public abstract MarathonBuilder setWorkspace(final FilePath ws);

    /**
     * Compose the builder. This populates certain fields with the proper Jenkins values and creates a
     * Marathon Client.
     *
     * @return This builder
     * @see mesosphere.marathon.client.MarathonClient
     */
    public abstract MarathonBuilder build();

    /**
     * Write the JSON that will be sent to the target Marathon instance to filename.
     *
     * @param filename File to write rendered JSON
     * @return This builder
     * @throws InterruptedException when issues encountered with filesystem
     * @throws MarathonFileInvalidException when Marathon config file is not a file
     * @throws IOException on IO issues
     */
    public abstract MarathonBuilder toFile(final String filename)
            throws InterruptedException, MarathonFileInvalidException, IOException;

    /**
     * Write the JSON that will be sent to the target Marathon instance to the default file
     * (marathon-rendered-${BUILD_NUMBER}.json).
     *
     * @return This builder
     * @throws InterruptedException when issues encountered with filesystem
     * @throws IOException on IO issues
     * @throws MarathonFileInvalidException when Marathon config file is not a file
     * @see #toFile(String)
     */
    public abstract MarathonBuilder toFile()
            throws InterruptedException, IOException, MarathonFileInvalidException;
}
