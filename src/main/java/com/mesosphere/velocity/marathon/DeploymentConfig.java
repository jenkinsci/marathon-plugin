package com.mesosphere.velocity.marathon;

import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Application Deployment Configuration
 *
 * @author luketornquist
 * @since 1.4.1
 */
public class DeploymentConfig implements Describable<DeploymentConfig> {

    private boolean forceUpdate;
    private List<MarathonUri> uris;
    private List<MarathonLabel> labels;
    private String appId;
    private String docker;
    private String filename;

    @DataBoundConstructor
    public DeploymentConfig(boolean forceUpdate, List<MarathonUri> uris, List<MarathonLabel> labels,
                            String appId, String docker, String filename) {
        this.forceUpdate = forceUpdate;
        this.uris = uris == null ? new ArrayList<MarathonUri>() : uris;
        this.labels = labels == null ? new ArrayList<MarathonLabel>() : labels;
        this.appId = appId;
        this.docker = docker;
        this.filename = filename;
    }

    public String getAppId() {
        return appId;
    }

    @DataBoundSetter
    public void setAppId(@Nonnull final String appId) {
        this.appId = appId;
    }

    public String getFilename() {
        return filename;
    }

    @DataBoundSetter
    public void setFilename(@Nonnull final String filename) {
        if (filename.trim().length() > 0)
            this.filename = filename;
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

    public String getDocker() {
        return docker;
    }

    @DataBoundSetter
    public void setDocker(@Nonnull final String docker) {
        this.docker = docker;
    }

    /**
     * Used by jelly or stapler to determine checkbox state.
     *
     * @return True if Force Update is enabled; False otherwise.
     */
    public boolean isForceUpdate() {
        return getForceUpdate();
    }

    public boolean getForceUpdate() {
        return this.forceUpdate;
    }

    @DataBoundSetter
    public void setForceUpdate(final boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    @Override
    public Descriptor<DeploymentConfig> getDescriptor() {
        return Hudson.getInstance().getDescriptorOrDie(getClass());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DeploymentConfig> {
        @Override
        public String getDisplayName() { return "Deployment Configuration"; }
    }
}
