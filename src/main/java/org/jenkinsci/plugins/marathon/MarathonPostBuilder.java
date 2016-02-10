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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by colin on 2/4/16.
 */
public class MarathonPostBuilder extends Notifier {
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    private final String url;
    private final String appid;
    private final String docker;
    private final List<MarathonUri> uris;
    private final List<MarathonLabel> labels;
    private final boolean runFailed;

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


    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final boolean buildSucceed = build.getResult() == Result.SUCCESS;

        if (buildSucceed) {
            final EnvVars envVars = build.getEnvironment(listener);
            final String appId = Util.replaceMacro(this.appid, envVars);
            build.setResult(Result.FAILURE);
        }

        return build.getResult() == Result.SUCCESS;
    }

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        // use a HEAD request for HTTP URLs; this will prevent trying
        // to read a large image, page, or asset.
        private final static String HTTP_REQUEST_METHOD = "HEAD";
        // HTTP timeout in milliseconds (5 seconds total)
        private final static int HTTP_TIMEOUT_IN_MILLIS = 5000;

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
                return FormValidation.error("URL did not return a 200 response.");
            return FormValidation.ok();
        }

        public FormValidation doCheckUrl(@QueryParameter String value) {
            return verifyUrl(value);
        }

        public FormValidation doCheckAppid(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() > 0 && value.length() < 3)
                return FormValidation.warning("Isn't the name too short?");
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
