package org.jenkinsci.plugins.marathon;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.marathon.fields.MarathonLabel;
import org.jenkinsci.plugins.marathon.fields.MarathonUri;
import org.jenkinsci.plugins.marathon.interfaces.AppConfig;
import org.jenkinsci.plugins.marathon.interfaces.MarathonBuilder;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class MarathonStep extends AbstractStepImpl implements AppConfig {
    private final String              url;
    private       List<MarathonUri>   marathonUris;
    private       List<String>        uris;
    private       List<MarathonLabel> labels;
    private       String              appid;
    private       String              docker;

    @DataBoundConstructor
    public MarathonStep(final String url) {
        this.url = url;
        this.uris = new ArrayList<String>(5);
        this.marathonUris = new ArrayList<MarathonUri>(5);
        this.labels = new ArrayList<MarathonLabel>(5);
    }

    @Override
    public String getAppId() {
        return this.appid;
    }

    public String getUrl() {
        return url;
    }

    public String getDocker() {
        return docker;
    }

    @DataBoundSetter
    public void setDocker(String docker) {
        this.docker = docker;
    }

    public List<MarathonUri> getUris() {
        return marathonUris;
    }

    @DataBoundSetter
    public void setUris(List<String> uris) {
        for (final String s : uris) {
            final MarathonUri marathonUri = new MarathonUri(s);
            if (!marathonUris.contains(marathonUri)) {
                marathonUris.add(marathonUri);
            }
        }
        this.uris = uris;
    }

    public List<MarathonLabel> getLabels() {
        return labels;
    }

    @DataBoundSetter
    public void setLabels(List<MarathonLabel> labels) {
        this.labels = labels;
    }

    public String getAppid() {
        return appid;
    }

    @DataBoundSetter
    public void setAppid(String appid) {
        this.appid = appid;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
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
            return "Marathon deployment";
        }
    }

    public static class MarathonStepExecution extends AbstractSynchronousStepExecution<Void> {
        private static final Logger LOGGER = Logger.getLogger(MarathonStepExecution.class.getName());

        @StepContextParameter
        private transient TaskListener listener;

        @StepContextParameter
        private transient FilePath ws;

        @StepContextParameter
        private transient Run<?, ?> build;

        @StepContextParameter
        private transient Launcher launcher;

        @StepContextParameter
        private transient EnvVars envVars;

        @Inject
        private transient MarathonStep step;

        @Override
        protected Void run() throws Exception {
            MarathonBuilder.getBuilder(step).setEnvVars(envVars).setWorkspace(ws).read().build().toFile().update();
            return null;
        }
    }
}
