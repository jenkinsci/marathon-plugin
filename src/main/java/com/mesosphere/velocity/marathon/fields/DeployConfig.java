package com.mesosphere.velocity.marathon.fields;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

/**
 * Deploy Configuration
 *
 * @author luketornquist
 * @since 1/21/17
 */
public class DeployConfig extends AbstractDescribableImpl<DeployConfig> {
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    private final String filename;
    private boolean forceUpdate;
    private String appId;
    private String dockerImage;

    @DataBoundConstructor
    public DeployConfig(final String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public boolean isForceUpdate() {
        return getForceUpdate();
    }

    public boolean getForceUpdate() {
        return forceUpdate;
    }

    @DataBoundSetter
    public void setForceUpdate(final boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    public String getAppId() {
        return appId;
    }

    @DataBoundSetter
    public void setAppId(@Nonnull final String appId) {
        this.appId = appId;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    @DataBoundSetter
    public void setDockerImage(@Nonnull final String dockerImage) {
        this.dockerImage = dockerImage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeployConfig)) {
            return false;
        }
        DeployConfig that = (DeployConfig) o;
        return filename.equals(that.filename);
    }

    @Override
    public int hashCode() {
        return filename.hashCode();
    }

    public static class DescriptorImpl extends Descriptor<DeployConfig> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Deployment Configuration";
        }
    }
}
