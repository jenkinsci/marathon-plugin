package com.mesosphere.velocity.marathon.fields;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

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

    @DataBoundConstructor
    public DeployConfig(final String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
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
