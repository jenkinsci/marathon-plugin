package com.mesosphere.velocity.marathon;

import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import com.mesosphere.velocity.marathon.util.MarathonBuilderUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class MarathonStep extends AbstractStepImpl {
    private final ArrayList<DeploymentConfig> configs;
    private final String              url;
    private       String              credentialsId;

    @DataBoundConstructor
    public MarathonStep(final String url, final List<DeploymentConfig> configs) {
        this.url = MarathonBuilderUtils.rmSlashFromUrl(url);
        this.configs = new ArrayList<>(Util.fixNull(configs));
    }

    public ArrayList<DeploymentConfig> getConfigs() {
        return configs;
    }

    public String getUrl() {
        return url;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(final String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        @Inject
        private MarathonRecorder.DescriptorImpl delegate;

        public DescriptorImpl() {
            super(MarathonStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "marathon";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Marathon Deployment";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project) {
            return delegate.doFillCredentialsIdItems(project);
        }
    }

    public static class MarathonStepExecution extends AbstractSynchronousStepExecution<Void> {
        private static final long serialVersionUID = 6213649171165833159L;
        /*
         * Need the listener to append to console log.
        */
        @StepContextParameter
        transient         TaskListener listener;
        @StepContextParameter
        private transient FilePath     ws;
        @StepContextParameter
        private transient EnvVars      envVars;
        @Inject
        private transient MarathonStep step;

        @Override
        protected Void run() throws Exception {
            for (DeploymentConfig config : step.getConfigs()) {
                MarathonBuilder
                        .getBuilder(step.getUrl(), step.getCredentialsId(), config)
                        .setEnvVars(envVars)
                        .setWorkspace(ws)
                        .read(config.getFilename())
                        .build()
                        .toFile()
                        .update();
            }
            return null;
        }
    }
}
