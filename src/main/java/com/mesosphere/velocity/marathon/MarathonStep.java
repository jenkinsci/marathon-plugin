package com.mesosphere.velocity.marathon;

import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.interfaces.AppConfig;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class MarathonStep extends AbstractStepImpl implements AppConfig {
    private final String              url;
    private       List<String>        uris;
    private       Map<String, String> labels;   // this does not work :(
    private       String              appid;
    private       String              docker;
    private       String              filename;

    @DataBoundConstructor
    public MarathonStep(final String url) {
        this.url = url;
        this.uris = new ArrayList<String>(5);
        this.labels = new HashMap<String, String>(5);
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
        final List<MarathonUri> marathonUris = new ArrayList<MarathonUri>(this.uris.size());
        for (final String u : this.uris) {
            marathonUris.add(new MarathonUri(u));
        }
        return marathonUris;
    }

    @DataBoundSetter
    public void setUris(List<String> uris) {
        this.uris = uris;
    }

    public List<MarathonLabel> getLabels() {
        final List<MarathonLabel> marathonLabels = new ArrayList<MarathonLabel>(this.labels.size());
        for (final Map.Entry<String, String> label : this.labels.entrySet()) {
            marathonLabels.add(new MarathonLabel(label.getKey(), label.getValue()));
        }
        return marathonLabels;
    }

    @DataBoundSetter
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public String getAppid() {
        return appid;
    }

    @DataBoundSetter
    public void setAppid(String appid) {
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
            return "Marathon Deployment";
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
            MarathonBuilder
                    .getBuilder(step)
                    .setEnvVars(envVars)
                    .setWorkspace(ws)
                    .read(step.filename)
                    .build()
                    .toFile()
                    .update();
            return null;
        }
    }
}
