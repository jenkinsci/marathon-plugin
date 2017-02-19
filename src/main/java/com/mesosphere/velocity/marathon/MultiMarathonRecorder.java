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
import com.mesosphere.velocity.marathon.fields.DeployConfig;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import com.mesosphere.velocity.marathon.util.MarathonBuilderUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.Result;
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

/**
 * Multi-Marathon Recorder
 *
 * @author luketornquist
 * @since 1/21/17
 */
public class MultiMarathonRecorder extends Recorder {
    @Extension
    public static final  DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    private static final Logger LOGGER     = Logger.getLogger(MarathonRecorder.class.getName());
    private final String                        url;
    private       boolean                       injectJenkinsVariables;
    private       String                        credentialsId;
    private       ArrayList<DeployConfig>       deployments;

    @DataBoundConstructor
    public MultiMarathonRecorder(final String url) {
        this.url = MarathonBuilderUtils.rmSlashFromUrl(url);
    }

    public String getUrl() {
        return url;
    }

    public String getCredentialsId() {
        return this.credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(final String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public boolean isInjectJenkinsVariables() {
        return injectJenkinsVariables;
    }

    public boolean getInjectJenkinsVariables() {
        return injectJenkinsVariables;
    }

    @DataBoundSetter
    public void setInjectJenkinsVariables(boolean injectJenkinsVariables) {
        this.injectJenkinsVariables = injectJenkinsVariables;
    }

    public List<DeployConfig> getDeployments() {
        return deployments;
    }

    @DataBoundSetter
    public void setDeployments(@Nonnull final List<DeployConfig> deployments) {
        this.deployments = new ArrayList<>(deployments);
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
     * "[Simple Marathon]".
     *
     * @param logger a build's logger
     * @param text   message to log
     */
    private void log(final PrintStream logger, final String text) {
        logger.println("[Multi-Marathon] " + text);
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        final boolean     buildSucceed = build.getResult() == null || build.getResult() == Result.SUCCESS;
        final EnvVars     envVars      = build.getEnvironment(listener);
        final PrintStream logger       = listener.getLogger();
        envVars.overrideAll(build.getBuildVariables());

        if (buildSucceed) {
            for (DeployConfig deployConfig : this.deployments) {
                try {
                    final MarathonBuilder builder = MarathonBuilder.getBuilder(envVars, this.url, this.credentialsId, this.injectJenkinsVariables)
                            .setConfig(deployConfig)
                            .setWorkspace(build.getWorkspace())
                            .setLogger(logger)
                            .read(deployConfig.getFilename())
                            .build();

                    // update & possible retry
                    boolean retry = true;
                    int retryCount = 0;
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
        }
        return build.getResult() == Result.SUCCESS;
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
            return "Multi-Marathon Deployment";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
