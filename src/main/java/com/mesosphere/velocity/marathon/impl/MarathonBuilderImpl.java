package com.mesosphere.velocity.marathon.impl;

import com.auth0.jwt.JWTAlgorithmException;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.mesosphere.velocity.marathon.exceptions.AuthenticationException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileInvalidException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileMissingException;
import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.interfaces.AppConfig;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import com.mesosphere.velocity.marathon.interfaces.TokenAuthProvider;
import com.mesosphere.velocity.marathon.util.MarathonBuilderUtils;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Util;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.MarathonClient;
import mesosphere.marathon.client.model.v2.App;
import mesosphere.marathon.client.utils.MarathonException;
import mesosphere.marathon.client.utils.ModelUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
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

    /**
     * Perform the actual update call to Marathon. If a 401 (Unauthenticated) response is received,
     * this will try to retrieve a new token from DC/OS using JWT credentials.
     *
     * @return this Marathon builder
     * @throws MarathonException
     * @throws AuthenticationException
     */
    @Override
    public MarathonBuilder update() throws MarathonException, AuthenticationException {
        if (app != null) {
            final UsernamePasswordCredentials userCredentials  = MarathonBuilderUtils.getUsernamePasswordCredentials(config.getCredentialsId());
            final StringCredentials           tokenCredentials = MarathonBuilderUtils.getTokenCredentials(config.getCredentialsId());

            try {
                doUpdate(userCredentials, tokenCredentials);
            } catch (MarathonException marathonException) {
                LOGGER.warning("Marathon Exception: " + marathonException.getMessage());
                if (marathonException.getStatus() != 401) {
                    throw marathonException;
                }
                LOGGER.warning("Received 401 when updating Marathon application.");

                // fetch a new token and update the credential store
                final StringCredentials newTokenCredentials = doDcosTokenUpdate(tokenCredentials);
                // use the new token
                doUpdate(userCredentials, newTokenCredentials);
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

    private StringCredentials doDcosTokenUpdate(final StringCredentials tokenCredentials) throws AuthenticationException, MarathonException {// the next String credentials are of the JSON variety
        final StringCredentials dcosCredentials = MarathonBuilderUtils.getTokenCredentials(config.getServiceAccountId());

        if (dcosCredentials != null) {
            final TokenAuthProvider provider = new DcosAuthImpl(dcosCredentials);
            try {
                final String token = provider.getToken();

                if (token == null) {
                    throw new AuthenticationException("Failed to retrieve authentication token from DC/OS.");
                }

                // retrieved a new token, now to update the existing credential in `tokenCredentials`
                final StringCredentials newTokenCredentials = new StringCredentialsImpl(
                        tokenCredentials.getScope(),
                        tokenCredentials.getId(),
                        tokenCredentials.getDescription(),
                        Secret.fromString(token));
                updateTokenCredentials(tokenCredentials, newTokenCredentials);
                return newTokenCredentials;
            } catch (IOException e) {
                e.printStackTrace();
                throw new AuthenticationException(e.getMessage());
            } catch (JWTAlgorithmException e) {
                // requested algorithm is not supported
                e.printStackTrace();
                throw new AuthenticationException(e.getMessage());
            } catch (InvalidKeyException e) {
                e.printStackTrace();
                throw new AuthenticationException(e.getMessage());
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                throw new AuthenticationException(e.getMessage());
            } catch (InvalidKeySpecException e) {
                e.printStackTrace();
                throw new AuthenticationException(e.getMessage());
            } catch (NoSuchProviderException e) {
                e.printStackTrace();
                throw new AuthenticationException(e.getMessage());
            }
        }

        return tokenCredentials;
    }

    private void updateTokenCredentials(final StringCredentials tokenCredentials, final Credentials creds) throws IOException {
        final SystemCredentialsProvider.ProviderImpl systemProvider = ExtensionList.lookup(CredentialsProvider.class)
                .get(SystemCredentialsProvider.ProviderImpl.class);
        final CredentialsStore credentialsStore = systemProvider.getStore(Jenkins.getInstance());

        // there should only be one credential to update, but there are multiple domains
        // that need to be searched. wasFound will track whether the proper credential
        // was located, and if so break out of both loops.
        boolean wasFound = false;
        for (Domain d : credentialsStore.getDomains()) {
            List<Credentials> credentialsList = credentialsStore.getCredentials(d);
            for (Credentials c : credentialsList) {
                if (c instanceof StringCredentials) {
                    // cast
                    final StringCredentials stringCredentials = (StringCredentials) c;
                    if (tokenCredentials.getId().equals(stringCredentials.getId())) {
                        final boolean wasUpdated = credentialsStore.updateCredentials(d, c, creds);
                        if (!wasUpdated) {
                            LOGGER.warning("Updating Token credential failed during update call.");
                        }
                        // set this as found, even if update itself failed.
                        wasFound = true;
                        break;
                    }
                }
            }

            // if the target credential was found, no need to check more domains.
            if (wasFound) {
                break;
            }
        }

        if (!wasFound) {
            LOGGER.warning("Token credential was not found in the Credentials Store.");
        }
    }

    private void doUpdate(UsernamePasswordCredentials userCredentials, StringCredentials tokenCredentials) throws MarathonException {
        getMarathonClient(userCredentials, tokenCredentials)
                .updateApp(app.getId(), app, config.getForceUpdate());
    }

    private Marathon getMarathonClient(UsernamePasswordCredentials userCredentials, StringCredentials credentials) {
        if (userCredentials != null) {
            return MarathonClient
                    .getInstanceWithBasicAuth(config.getUrl(), userCredentials.getUsername(), userCredentials.getPassword().getPlainText());
        } else if (credentials != null) {
            return MarathonClient
                    .getInstanceWithTokenAuth(config.getUrl(), credentials.getSecret().getPlainText());
        } else {
            return MarathonClient.getInstance(config.getUrl());
        }
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
