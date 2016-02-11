package org.jenkinsci.plugins.marathon;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by colin on 2/4/16.
 */
public class MarathonPostBuilder extends Notifier {
    @Extension
    public static final  DescriptorImpl DESCRIPTOR                       = new DescriptorImpl();
    public static final  String         WORKSPACE_MARATHON_JSON          = "${WORKSPACE}/marathon.json";
    public static final  String         WORKSPACE_MARATHON_RENDERED_JSON = "${WORKSPACE}/marathon-rendered-${BUILD_NUMBER}.json";
    public static final  String         JSON_CONTAINER_FIELD             = "container";
    public static final  String         JSON_DOCKER_FIELD                = "docker";
    public static final  String         JSON_DOCKER_IMAGE_FIELD          = "image";
    public static final  String         JSON_ID_FIELD                    = "id";
    public static final  String         JSON_URI_FIELD                   = "uris";
    public static final  String         JSON_EMPTY_CONTAINER_OBJ         = "{\"type\": \"DOCKER\"}";
    private static final Logger         LOGGER                           = Logger.getLogger(MarathonPostBuilder.class.getName());
    private final String              url;
    private final String              appid;
    private final String              docker;
    private final List<MarathonUri>   uris;
    private final List<MarathonLabel> labels;
    private final boolean             runFailed;

    @DataBoundConstructor
    public MarathonPostBuilder(final String url, final String appid, final String docker, final List<MarathonUri> uris, final List<MarathonLabel> labels, final boolean runFailed) {
        this.url = url;
        this.appid = appid;
        this.docker = docker;
        this.runFailed = runFailed;

        this.uris = new ArrayList<MarathonUri>(5);
        if (uris != null && !uris.isEmpty())
            this.uris.addAll(uris);

        this.labels = new ArrayList<MarathonLabel>(5);
        if (labels != null && !labels.isEmpty())
            this.labels.addAll(labels);
    }

    public String getUrl() {
        return url;
    }

    public boolean isRunFailed() {
        return runFailed;
    }

    public String getAppid() {
        return appid;
    }

    public String getDocker() {
        return docker;
    }

    public List<MarathonUri> getUris() {
        return uris;
    }

    public List<MarathonLabel> getLabels() {
        return labels;
    }

    public boolean getRunFailed() {
        return runFailed;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        /*
         * This does not need any isolation.
         */
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        /*
         * This should be run before the build is finalized.
         */
        return false;
    }

    /**
     * Write json to file filename.
     *
     * @param filename File name for new file
     * @param json     JSON data
     * @throws IOException If filename is a directory or a file operation encounters
     *                     an issue
     */
    private void writeJsonToFile(final String filename, final JSONObject json) throws IOException {
        final File toFile = new File(filename);

        if (toFile.exists() && toFile.isDirectory())
            throw new IOException("File '" + filename + "' is a directory; not overwriting.");

        final Writer writer = new BufferedWriter(new FileWriter(new File(filename)));
        writer.write(json.toString(4));
        writer.flush();
        writer.close();
        LOGGER.info("Wrote JSON to '" + filename + "'");
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final boolean buildSucceed = build.getResult() == Result.SUCCESS;
        final EnvVars envVars      = build.getEnvironment(listener);
        final String  fileName     = Util.replaceMacro(WORKSPACE_MARATHON_JSON, envVars);
        final File    marathonFile = new File(fileName);

        if ((buildSucceed || runFailed)
                && marathonFile.exists() && !marathonFile.isDirectory()) {
            final String     content      = FileUtils.readFileToString(marathonFile);
            final JSONObject marathonJson = JSONObject.fromObject(content);

            if (marathonJson != null && !marathonJson.isEmpty() && !marathonJson.isArray()) {
                final String marathonUrl = Util.replaceMacro(this.url, envVars);

                setJsonId(envVars, marathonJson);
                setJsonDockerImage(envVars, marathonJson);
                // TODO: Add checkbox to toggle removal vs merging
                marathonJson.remove(JSON_URI_FIELD);
                setJsonUris(envVars, marathonJson);

                /*
                 * JSON is done being constructed; done merging marathon.json
                 * with Jenkins configuration and environment variables.
                 */
                final String renderedFilename = Util.replaceMacro(WORKSPACE_MARATHON_RENDERED_JSON, envVars);
                try {
                    writeJsonToFile(renderedFilename, marathonJson);
                } catch (IOException e) {
                    LOGGER.warning("Exception encountered when writing rendered JSON to '" + renderedFilename + "'");
                    LOGGER.warning(e.getLocalizedMessage());
                }

                build.setResult(Result.FAILURE);
            }
        }

        return build.getResult() == Result.SUCCESS;
    }

    /**
     * Set the root "uris" JSON array with the URIs configured within
     * the Jenkins UI. This handles transforming Environment Variables
     * to their actual values.
     *
     * @param envVars Jenkins environment variables
     * @param json    Root JSON object
     */
    private void setJsonUris(final EnvVars envVars, final JSONObject json) {
        for (MarathonUri uri : uris) {
            json.accumulate(JSON_URI_FIELD, Util.replaceMacro(uri.getUri(), envVars));
        }
    }

    /**
     * Set the root "id" value. This handles transforming Environment
     * Variables to their actual values.
     *
     * @param envVars      Jenkins environment variables
     * @param marathonJson Root JSON object
     */
    private void setJsonId(final EnvVars envVars, final JSONObject marathonJson) {
        if (appid != null)
            marathonJson.put(JSON_ID_FIELD, Util.replaceMacro(this.appid, envVars));
    }

    /**
     * Set the docker "image" JSON value. This will create and set
     * empty JSON objects for container and docker if they do not
     * already exist within <code>marathonJson</code>.
     * <p>
     * This handles transforming Environment Variables to their
     * actual values.
     *
     * @param envVars      Jenkins environment variables
     * @param marathonJson Root JSON object
     */
    private void setJsonDockerImage(final EnvVars envVars, final JSONObject marathonJson) {
        if (docker != null) {
            // get container -> docker -> image
            if (!marathonJson.has(JSON_CONTAINER_FIELD)) {
                marathonJson.element(JSON_CONTAINER_FIELD, JSONObject.fromObject(JSON_EMPTY_CONTAINER_OBJ));
            }

            final JSONObject container = marathonJson.getJSONObject(JSON_CONTAINER_FIELD);
            if (!container.has(JSON_DOCKER_FIELD)) {
                container.element(JSON_DOCKER_FIELD, new JSONObject());
            }

            final JSONObject docker = container.getJSONObject(JSON_DOCKER_FIELD);
            docker.element(JSON_DOCKER_IMAGE_FIELD, Util.replaceMacro(this.docker, envVars));
        }
    }

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        // use a HEAD request for HTTP URLs; this will prevent trying
        // to read a large image, page, or asset.
        private final static String HTTP_REQUEST_METHOD    = "HEAD";
        // HTTP timeout in milliseconds (5 seconds total)
        private final static int    HTTP_TIMEOUT_IN_MILLIS = 5000;

        public DescriptorImpl() {
            load();
        }

        private boolean isUrl(final String url) {
            boolean valid = false;

            if (url != null && url.length() > 0) {
                try {
                    new URL(url);
                    valid = true;
                } catch (MalformedURLException e) {
                    // malformed; ignore
                }
            }

            return valid;
        }

        private boolean returns200Response(final String url) {
            boolean responding = false;

            try {
                final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(HTTP_TIMEOUT_IN_MILLIS);
                conn.setRequestMethod(HTTP_REQUEST_METHOD);
                conn.connect();         // connect
                // validate the response code is a 20x code
                responding = conn.getResponseCode() >= 200 && conn.getResponseCode() < 300;
                conn.disconnect();      // disconnect and cleanup
            } catch (MalformedURLException e) {
                // malformed; ignore
            } catch (IOException e) {
                // problem with connection :shrug:
            }

            return responding;
        }

        private FormValidation verifyUrl(final String url) {
            if (!isUrl(url))
                return FormValidation.error("Not a valid URL");
            if (!returns200Response(url))
                return FormValidation.warning("URL did not return a 200 response.");
            return FormValidation.ok();
        }

        public FormValidation doCheckUrl(@QueryParameter String value) {
            return verifyUrl(value);
        }

        public FormValidation doCheckAppid(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.ok();
        }

        public FormValidation doCheckDocker(@QueryParameter String value) {
            return FormValidation.ok();
        }

        public FormValidation doCheckUri(@QueryParameter String value) {
            return verifyUrl(value);
        }

        public FormValidation doCheckLabelName(@QueryParameter String value) {
            return FormValidation.ok();
        }

        public FormValidation doCheckLabelValue(@QueryParameter String value) {
            return FormValidation.ok();
        }

        @Override
        public String getDisplayName() {
            return "Marathon Deployments";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
