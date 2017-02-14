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
import mesosphere.marathon.client.utils.MarathonException;
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
    private       String              appid;
    private       String              docker;
    private       boolean             dockerForcePull;
    private       String              filename;
    private       String              credentialsId;
    private       boolean             forceUpdate;

    @DataBoundConstructor
    public MarathonRecorder(final String url) {
        this.url = MarathonBuilderUtils.rmSlashFromUrl(url);

        this.uris = new ArrayList<MarathonUri>(5);
        this.labels = new ArrayList<MarathonLabel>(5);
    }

    public String getAppid() {
        return appid;
    }

    @DataBoundSetter
    public void setAppid(@Nonnull final String appid) {
        this.appid = appid;
    }

    public String getFilename() {
        return filename;
    }

    @DataBoundSetter
    public void setFilename(@Nonnull final String filename) {
        if (filename.trim().length() > 0)
            this.filename = filename;
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
    private void log(final PrintStream logger, final String text) {
        logger.println("[Marathon] " + text);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        final boolean     buildSucceed = build.getResult() == null || build.getResult() == Result.SUCCESS;
        final EnvVars     envVars      = build.getEnvironment(listener);
        final PrintStream logger       = listener.getLogger();
        envVars.overrideAll(build.getBuildVariables());

        if (buildSucceed) {
            try {
                final MarathonBuilder builder = MarathonBuilder.getBuilder(this)
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

    public String getUrl() {
        return url;
    }

    @Override
    public boolean getForceUpdate() {
        return forceUpdate;
    }

    public String getDocker() {
        return docker;
    }

    public boolean getDockerForcePull() {
        return dockerForcePull;
    }

    public String getCredentialsId() {
        return this.credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(final String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public List<MarathonUri> getUris() {
        return uris;
    }

    @DataBoundSetter
    public void setUris(final List<MarathonUri> uris) {
        this.uris = uris;
    }

    public List<MarathonLabel> getLabels() {
        return labels;
    }

    @DataBoundSetter
    public void setLabels(final List<MarathonLabel> labels) {
        this.labels = labels;
    }

    @DataBoundSetter
    public void setDocker(@Nonnull final String docker) {
        this.docker = docker;
    }

    @DataBoundSetter
    public void setDockerForcePull(@Nonnull final boolean dockerForcePull) {
        this.dockerForcePull = dockerForcePull;
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
    public void setForceUpdate(final boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
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

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item) {
            return new StandardListBoxModel().withEmptySelection().withMatching(
                    CredentialsMatchers.anyOf(
                            CredentialsMatchers.instanceOf(StringCredentials.class),
                            CredentialsMatchers.instanceOf(UsernamePasswordCredentials.class)
                    ),
                    CredentialsProvider.lookupCredentials(StandardCredentials.class, item, null, Collections.<DomainRequirement>emptyList())
            );
        }

        private FormValidation verifyUrl(final String url) {
            if (!isUrl(url))
                return FormValidation.error("Not a valid URL");
            return FormValidation.ok();
        }

        public FormValidation doCheckUrl(@QueryParameter final String value) {
            return verifyUrl(value);
        }

        public FormValidation doCheckUri(@QueryParameter final String value) {
            return verifyUrl(value);
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
