package com.mesosphere.velocity.marathon;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.mesosphere.velocity.marathon.exceptions.AuthenticationException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileInvalidException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileMissingException;
import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.fields.MarathonVars;
import com.mesosphere.velocity.marathon.interfaces.AppConfig;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import com.mesosphere.velocity.marathon.util.MarathonBuilderUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import mesosphere.marathon.client.MarathonException;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class MarathonRecorder extends Recorder implements AppConfig {
    @Extension
    public static final  DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    private static final Logger         LOGGER     = Logger.getLogger(MarathonRecorder.class.getName());
    private final String              url;
    private       List<MarathonUri>   uris;
    private       List<MarathonLabel> labels;
    private       List<MarathonVars>  env;
    private       String              appid;
    private       String              docker;
    private       boolean             dockerForcePull;
    private       String              filename;
    private       String              credentialsId;
    private       boolean             forceUpdate;
    private long timeout;

    @DataBoundConstructor
    public MarathonRecorder(String url) {
        this.url = MarathonBuilderUtils.rmSlashFromUrl(url);

        this.uris = new ArrayList<>(5);
        this.labels = new ArrayList<>(5);
        this.env = new ArrayList<>(5);
    }

    public String getAppid() {
        return this.appid;
    }

    @DataBoundSetter
    public void setAppid(@Nonnull String appid) {
        this.appid = appid;
    }

    public String getFilename() {
        return this.filename;
    }

    @DataBoundSetter
    public void setFilename(@Nonnull String filename) {
        if (filename.trim().length() > 0) {
            this.filename = filename;
        }
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
     * Write text to the build's console log (logger), prefixed with
     * "[Marathon]".
     *
     * @param logger a build's logger
     * @param text   message to log
     */
    private void log(PrintStream logger, String text) {
        logger.println("[Marathon] " + text);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        boolean buildSucceed = build.getResult() == null || build.getResult() == Result.SUCCESS;
        EnvVars envVars = build.getEnvironment(listener);
        PrintStream logger = listener.getLogger();
        envVars.overrideAll(build.getBuildVariables());

        if (buildSucceed) {
            try {
                MarathonBuilder builder = MarathonBuilder.getBuilder(this)
                        .setEnvVars(envVars).setWorkspace(build.getWorkspace())
                        .read(this.filename)
                        .build().toFile();

                // update & possible retry
                boolean retry      = true;
                int     retryCount = 0;
                while (retry && retryCount < 3) {
                    try {
                        builder.update();
                        retry = false;
                        log(logger, "Marathon application updated.");
                    } catch (MarathonException e) {
                        // 409 is app already deployed and should trigger retry
                        // 4xx and 5xx errors are build failures
                        if (e.getStatus() != 409
                                && (e.getStatus() >= 400 && e.getStatus() < 600)) {
                            build.setResult(Result.FAILURE);
                            log(logger, "Failed to update Marathon application:");
                            log(logger, e.getMessage());
                            LOGGER.warning(e.getMessage());
                            retry = false;
                        } else {
                            // retry.
                            retryCount++;
                            Thread.sleep(5000L);    // 5 seconds
                        }
                    }
                }

                if (retry) {
                    build.setResult(Result.FAILURE);
                    log(logger, "Reached max retries updating Marathon application.");
                }
            } catch (MarathonFileMissingException e) {
                // "marathon.json" or whatever does not exist.
                build.setResult(Result.FAILURE);
                log(logger, "Application Definition not found:");
                log(logger, e.getMessage());
            } catch (MarathonFileInvalidException e) {
                // file is a directory or something.
                build.setResult(Result.FAILURE);
                log(logger, "Application Definition is not a file:");
                log(logger, e.getMessage());
            } catch (AuthenticationException e) {
                build.setResult(Result.FAILURE);
                log(logger, "Authentication to Marathon instance failed:");
                log(logger, e.getMessage());
            }

        }
        return build.getResult() == Result.SUCCESS;
    }

    @Override
    public String getAppId() {
        return this.appid;
    }

    @Override
    public String getUrl() {
        return this.url;
    }

    @Override
    public boolean getForceUpdate() {
        return this.forceUpdate;
    }

    @Override
    public String getDocker() {
        return this.docker;
    }

    @DataBoundSetter
    public void setDocker(@Nonnull String docker) {
        this.docker = docker;
    }

    @Override
    public boolean getDockerForcePull() {
        return this.dockerForcePull;
    }

    @DataBoundSetter
    public void setDockerForcePull(@Nonnull boolean dockerForcePull) {
        this.dockerForcePull = dockerForcePull;
    }

    @Override
    public String getCredentialsId() {
        return this.credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @Override
    public List<MarathonUri> getUris() {
        return this.uris;
    }

    @DataBoundSetter
    public void setUris(List<MarathonUri> uris) {
        this.uris = uris;
    }

    @Override
    public List<MarathonLabel> getLabels() {
        return this.labels;
    }

    @DataBoundSetter
    public void setLabels(List<MarathonLabel> labels) {
        this.labels = labels;
    }

    @Override
    public List<MarathonVars> getEnv() {
        return this.env;
    }

    @Override
    public long getTimeout() {
        return this.timeout;
    }

    @DataBoundSetter
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @DataBoundSetter
    public void setEnvironment(List<MarathonVars> env) {
        this.env = env;
    }

    /**
     * Used by jelly or stapler to determine checkbox state.
     *
     * @return True if Force Update is enabled; False otherwise.
     */
    public boolean isForceUpdate() {
        return getForceUpdate();
    }

    @DataBoundSetter
    public void setForceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
            load();
        }

        private boolean isUrl(String url) {
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

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item) {
            return new StandardListBoxModel().withEmptySelection().withMatching(
                    CredentialsMatchers.anyOf(
                            CredentialsMatchers.instanceOf(StringCredentials.class),
                            CredentialsMatchers.instanceOf(UsernamePasswordCredentials.class)
                    ),
                    CredentialsProvider.lookupCredentials(StandardCredentials.class, item, null, Collections.<DomainRequirement>emptyList())
            );
        }

        private FormValidation verifyUrl(String url) {
            if (!isUrl(url)) {
                return FormValidation.error("Not a valid URL");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckUrl(@QueryParameter String value) {
            return verifyUrl(value);
        }

        public FormValidation doCheckUri(@QueryParameter String value) {
            return verifyUrl(value);
        }

        public FormValidation doCheckTimeout(@QueryParameter String value) {
            try {
                Long.getLong(value);
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error("Invalid number format.");
            }
        }

        @Override
        public String getDisplayName() {
            return "Marathon Deployment";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
