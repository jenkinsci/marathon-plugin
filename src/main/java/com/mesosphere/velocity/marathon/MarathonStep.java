package com.mesosphere.velocity.marathon;

import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.interfaces.AppConfig;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import com.mesosphere.velocity.marathon.util.MarathonBuilderUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarathonStep extends AbstractStepImpl implements AppConfig {
    private final String              url;
    private       List<String>        uris;
    private       Map<String, String> labels;   // this does not work :(
    private       String              appid;
    private       String              id;
    private       String              docker;
    private       boolean             dockerForcePull;
    private       String              filename;
    private       String              credentialsId;
    private       boolean             forceUpdate;

    @DataBoundConstructor
    public MarathonStep(final String url) {
        this.url = MarathonBuilderUtils.rmSlashFromUrl(url);
        this.uris = new ArrayList<String>(5);
        this.labels = new HashMap<String, String>(5);
    }

    @Override
    public String getAppId() {
        return this.id;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public boolean getForceUpdate() {
        return forceUpdate;
    }

    @DataBoundSetter
    public void setForceUpdate(final boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    public String getDocker() {
        return docker;
    }

    public boolean getDockerForcePull() {
        return dockerForcePull;
    }

    @Override
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(final String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public List<MarathonUri> getUris() {
        final List<MarathonUri> marathonUris = new ArrayList<MarathonUri>(this.uris.size());
        for (final String u : this.uris) {
            marathonUris.add(new MarathonUri(u));
        }
        return marathonUris;
    }

    @DataBoundSetter
    public void setUris(final List<String> uris) {
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
    public void setLabels(final Map<String, String> labels) {
        this.labels = labels;
    }

    @DataBoundSetter
    public void setDocker(final String docker) {
        this.docker = docker;
    }

    @DataBoundSetter
    public void setDockerForcePull(final boolean dockerForcePull) {
        this.dockerForcePull = dockerForcePull;
    }

    /**
     * Get the application id for the "appid" field.
     *
     * @return application id
     * @deprecated use {@link #getId()}
     */
    @Deprecated
    public String getAppid() {
        return appid;
    }

    /**
     * Set the application id for the "appid" field.
     *
     * @param appid application id
     * @deprecated use {@link #setId(String)}
     */
    @Deprecated
    @DataBoundSetter
    public void setAppid(final String appid) {
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

    /**
     * Get the application id for the "id" field.
     *
     * @return application id
     * @since 1.3.3
     */
    public String getId() {
        return id;
    }

    /**
     * Set the application id for the "id" field.
     *
     * @param id application id
     * @since 1.3.3
     */
    @DataBoundSetter
    public void setId(final String id) {
        this.id = id;
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
            if (step.getAppid() != null && !step.getAppid().equals("")) {
                listener.getLogger().println("[Marathon] DEPRECATION WARNING: This configuration is using \"appid\" instead of \"id\". Please update this configuration.");
                step.setId(step.getAppid());
            }

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
