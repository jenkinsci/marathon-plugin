package com.mesosphere.velocity.marathon.interfaces;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
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
import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.MarathonClient;
import mesosphere.marathon.client.utils.MarathonException;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;

/**
 * This builds {@see MarathonClient}s from Jenkins, file system, and JSON pieces.
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
    private Credentials credentials;
    private Marathon marathonClient;
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

    public static MarathonBuilder getBuilder(final EnvVars envVars, final String url, final String credentialId) {
        return new MarathonBuilderApiImpl(envVars, url, credentialId);
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

    protected Credentials getCredentials() {
        return credentials;
    }

    protected Marathon getMarathonClient() {
        return marathonClient;
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
     * @throws MarathonException
     * @throws AuthenticationException
     */
    protected abstract void doUpdate() throws MarathonException, AuthenticationException;

    /**
     * Perform the actual update call to Marathon. If a 401 (Unauthenticated) response is received,
     * this will try to retrieve a new token from DC/OS using JWT credentials.
     *
     * @return this Marathon builder
     * @throws MarathonException       If Marathon does not return a 20x OK response
     * @throws AuthenticationException If an authentication provider was used and encountered a problem.
     */
    public MarathonBuilder update() throws MarathonException, AuthenticationException {
        try {
            executeUpdate(this.credentialsId);
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
                executeUpdate(this.credentialsId);
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

    private void executeUpdate(String credentialsId) throws MarathonException, AuthenticationException {
        this.credentials = MarathonBuilderUtils.getJenkinsCredentials(credentialsId, Credentials.class);
        initializeMarathonClient(this.credentials);
        doUpdate();
    }

    private void initializeMarathonClient(Credentials credentials) {
        if (credentials instanceof UsernamePasswordCredentials) {
            this.marathonClient = getMarathon((UsernamePasswordCredentials) credentials);
        } else if (credentials instanceof StringCredentials) {
            this.marathonClient = getMarathon((StringCredentials) credentials);
        } else {
            this.marathonClient = getMarathon();
        }
    }

    /**
     * Get a Marathon client with basic auth using the username and password within the provided credentials.
     *
     * @param credentials Username and password credentials
     * @return Marathon client with basic authentication filled in
     */
    private Marathon getMarathon(UsernamePasswordCredentials credentials) {
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
    private Marathon getMarathon(StringCredentials credentials) {
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

        return getMarathon();
    }

    /**
     * Get a default Marathon client. This does not include any authentication headers.
     *
     * @return Marathon client without authentication mechanisms
     */
    private Marathon getMarathon() {
        return MarathonClient.getInstance(getURL());
    }

    /**
     * Read in filename as JSON.
     *
     * @param filename Path to the JSON file
     * @return This builder
     * @throws IOException
     * @throws InterruptedException
     * @throws MarathonFileMissingException
     * @throws MarathonFileInvalidException
     */
    public abstract MarathonBuilder read(final String filename)
            throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException;

    /**
     * Read in default file (marathon.json) as JSON.
     *
     * @return This builder
     * @throws IOException
     * @throws InterruptedException
     * @throws MarathonFileMissingException
     * @throws MarathonFileInvalidException
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
     * @throws InterruptedException
     * @throws MarathonFileInvalidException
     * @throws IOException
     */
    public abstract MarathonBuilder toFile(final String filename)
            throws InterruptedException, MarathonFileInvalidException, IOException;

    /**
     * Write the JSON that will be sent to the target Marathon instance to the default file
     * (marathon-rendered-${BUILD_NUMBER}.json).
     *
     * @return This builder
     * @throws InterruptedException
     * @throws IOException
     * @throws MarathonFileInvalidException
     * @see #toFile(String)
     */
    public abstract MarathonBuilder toFile()
            throws InterruptedException, IOException, MarathonFileInvalidException;
}
