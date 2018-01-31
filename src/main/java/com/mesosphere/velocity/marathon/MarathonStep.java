package com.mesosphere.velocity.marathon;

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
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import mesosphere.marathon.client.MarathonException;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class MarathonStep extends AbstractStepImpl implements AppConfig {
    private final String              url;
    private       List<MarathonUri>   uris;
    private       List<MarathonLabel> labels;   // this does not work :(
    private       List<MarathonVars>  env;
    private       String              appid;
    private       String              id;
    private       String              docker;
    private       boolean             dockerForcePull;
    private       String              filename;
    private       String              credentialsId;
    private       boolean             forceUpdate;
    private       long                timeout;

    @DataBoundConstructor
    public MarathonStep(String url) {
        this.url = MarathonBuilderUtils.rmSlashFromUrl(url);
        this.uris = new ArrayList<MarathonUri>(5);
        this.labels = new ArrayList<MarathonLabel>(5);
        this.env = new ArrayList<MarathonVars>(5);
    }

    @Override
    public String getAppId() {
        return this.id;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public boolean getForceUpdate() {
        return this.forceUpdate;
    }

    @DataBoundSetter
    public void setForceUpdate(final boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    @Override
    public List<MarathonVars> getEnv() {
        final List<MarathonVars> marathonVarsList = new ArrayList<>(this.env.size());
        for (final MarathonVars envElem : this.env) {
            marathonVarsList.add(new MarathonVars(envElem.getName(), envElem.getValue()));
        }
        return marathonVarsList;
    }

    @DataBoundSetter
    public void setEnv(final List<MarathonVars> env) {
        this.env = env;
    }

    @Override
    public String getDocker() {
        return this.docker;
    }

    @DataBoundSetter
    public void setDocker(final String docker) {
        this.docker = docker;
    }

    @Override
    public boolean getDockerForcePull() {
        return this.dockerForcePull;
    }

    @DataBoundSetter
    public void setDockerForcePull(final boolean dockerForcePull) {
        this.dockerForcePull = dockerForcePull;
    }

    @Override
    public String getCredentialsId() {
        return this.credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(final String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @Override
    public List<MarathonUri> getUris() {
        final List<MarathonUri> marathonUris = new ArrayList<>(this.uris.size());
        for (final MarathonUri u : this.uris) {
            marathonUris.add(new MarathonUri(u.getUri()));
        }
        return marathonUris;
    }

    @DataBoundSetter
    public void setUris(final List<MarathonUri> uris) {
        this.uris = uris;
    }

    @Override
    public List<MarathonLabel> getLabels() {
        final List<MarathonLabel> marathonLabels = new ArrayList<>(this.labels.size());
        for (final MarathonLabel label : this.labels) {
            marathonLabels.add(new MarathonLabel(label.getName(), label.getValue()));
        }
        return marathonLabels;
    }

    @DataBoundSetter
    public void setLabels(final List<MarathonLabel> labels) {
        this.labels = labels;
    }

    /**
     * Get the application id for the "appid" field.
     *
     * @return application id
     * @deprecated use {@link #getId()}
     */
    @Deprecated
    public String getAppid() {
        return this.appid;
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
        return this.filename;
    }

    @DataBoundSetter
    public void setFilename(@Nonnull final String filename) {
        if (filename.trim().length() > 0)
            this.filename = filename;
    }

    @Override
    public long getTimeout() {
        return this.timeout;
    }

    @DataBoundSetter
    public void setTimeout(final long timeout) {
        this.timeout = timeout;
    }

    /**
     * Get the application id for the "id" field.
     *
     * @return application id
     * @since 1.3.3
     */
    public String getId() {
        return this.id;
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

        public FormValidation doCheckUrl(@QueryParameter final String value) {
            return this.delegate.doCheckUrl(value);
        }

        public FormValidation doCheckUri(@QueryParameter final String value) {
            return this.delegate.doCheckUri(value);
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
            return this.delegate.doFillCredentialsIdItems(project);
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
        @StepContextParameter
        private transient Run          run;
        @Inject
        private transient MarathonStep step;

        @Override
        protected Void run() throws Exception {
            if (this.step.getAppid() != null && !this.step.getAppid().equals("")) {
                this.listener.getLogger().println("[Marathon] DEPRECATION WARNING: This configuration is using \"appid\" instead of \"id\". Please update this configuration.");
                this.step.setId(this.step.getAppid());
            }

            try {
                MarathonBuilder
                        .getBuilder(this.step)
                        .setEnvVars(this.envVars)
                        .setWorkspace(this.ws)
                        .read(this.step.filename)
                        .build()
                        .toFile()
                        .update();
            } catch (MarathonException | MarathonFileInvalidException | MarathonFileMissingException me) {
                final String errorMsg = String.format("[Marathon] %s", me.getMessage());
                this.listener.error(errorMsg);
                this.run.setResult(Result.FAILURE);
            }

            return null;
        }
    }
}
