package com.mesosphere.velocity.marathon.impl;

import com.cloudbees.plugins.credentials.Credentials;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileInvalidException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileMissingException;
import com.mesosphere.velocity.marathon.fields.DeployConfig;
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
    private static final String INJECT_VARIABLE_TEMPLATE = "Injecting: [%s] as [%s]";
    private static final String JENKINS_BUILD_NAME_VARIABLE = "JENKINS_BUILD_NUMBER";
    private static final String JENKINS_JOB_NAME_VARIABLE = "JENKINS_JOB_NAME";
    private static final String JENKINS_GIT_COMMIT_VARIABLE = "JENKINS_GIT_COMMIT";
    private DeployConfig deployConfig;
    private JSONObject json;
    private FilePath workspace;
    private boolean injectJenkinsVariables;

    public MarathonBuilderApiImpl() {
        this(new EnvVars(), null, null, true);
    }

    public MarathonBuilderApiImpl(final EnvVars envVars, final String url, final String credentialId, final boolean injectJenkinsVariables) {
        super(envVars, url, credentialId);
        this.injectJenkinsVariables = injectJenkinsVariables;
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
    public MarathonBuilder setConfig(AppConfig config) {
        LOGGER.warning("MarathonBuilderApiImpl does not currently support 'AppConfig'-based configuration");
        return this;
    }

    @Override
    public MarathonBuilder setConfig(final DeployConfig config) {
        this.deployConfig = config;
        return this;
    }

    @Override
    public MarathonBuilder setWorkspace(final FilePath ws) {
        this.workspace = ws;
        return this;
    }

    @Override
    public MarathonBuilder build() {
        replaceValuesInJson();
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
        marathonApi.update(appId, this.json.toString(), deployConfig.getForceUpdate());
    }

    private void replaceValuesInJson() {
        replaceAppId();
        replaceDockerImage();
        injectEnvironmentVariables();
    }

    private void replaceAppId() {
        if (this.json != null && StringUtils.isNotBlank(this.deployConfig.getAppId())) {
            final String previousId = this.json.getString(MarathonBuilderUtils.JSON_ID_FIELD);
            final String newId = replaceMacro(deployConfig.getAppId());
            log(String.format("Replacing Application ID: [%s] => [%s]", previousId, newId));
            json.put(MarathonBuilderUtils.JSON_ID_FIELD, newId);
        }
    }

    private void replaceDockerImage() {
        if (this.json != null && StringUtils.isNotBlank(this.deployConfig.getDockerImage())) {
            // Verify that container exists in the JSON
            if (!this.json.has(MarathonBuilderUtils.JSON_CONTAINER_FIELD)) {
                this.json.element(MarathonBuilderUtils.JSON_CONTAINER_FIELD, JSONObject.fromObject(MarathonBuilderUtils.JSON_EMPTY_CONTAINER));
            }
            final JSONObject container = this.json.getJSONObject(MarathonBuilderUtils.JSON_CONTAINER_FIELD);
            // Verify that docker exists in the JSON (under container)
            if (!container.has(MarathonBuilderUtils.JSON_DOCKER_FIELD)) {
                container.element(MarathonBuilderUtils.JSON_DOCKER_FIELD, new JSONObject());
            }
            final JSONObject docker = container.getJSONObject(MarathonBuilderUtils.JSON_DOCKER_FIELD);

            final String previousImage = docker.getString(MarathonBuilderUtils.JSON_DOCKER_IMAGE_FIELD);
            final String newImage = replaceMacro(deployConfig.getDockerImage());
            log(String.format("Replacing Docker Image: [%s] => [%s]", previousImage, newImage));
            docker.put(MarathonBuilderUtils.JSON_DOCKER_IMAGE_FIELD, newImage);
        }
    }

    private void injectEnvironmentVariables() {
        if (this.json != null && this.injectJenkinsVariables) {
            // Verify that container exists in the JSON
            if (!this.json.has(MarathonBuilderUtils.JSON_ENV_FIELD)) {
                this.json.element(MarathonBuilderUtils.JSON_ENV_FIELD, new JSONObject());
            }
            final JSONObject env = this.json.getJSONObject(MarathonBuilderUtils.JSON_ENV_FIELD);
            log("Injecting Jenkins Environment Variables");
            final String buildNumber = replaceMacro("${BUILD_NUMBER}");
            env.put(JENKINS_BUILD_NAME_VARIABLE, buildNumber);
            log(String.format(INJECT_VARIABLE_TEMPLATE, JENKINS_BUILD_NAME_VARIABLE, buildNumber));
            final String jobName = replaceMacro("${JOB_NAME}");
            env.put(JENKINS_JOB_NAME_VARIABLE, jobName);
            log(String.format(INJECT_VARIABLE_TEMPLATE, JENKINS_JOB_NAME_VARIABLE, jobName));
            final String gitCommit = replaceMacro("${GIT_COMMIT}");
            env.put(JENKINS_GIT_COMMIT_VARIABLE, gitCommit);
            log(String.format(INJECT_VARIABLE_TEMPLATE, JENKINS_GIT_COMMIT_VARIABLE, gitCommit));
        }
    }
}
