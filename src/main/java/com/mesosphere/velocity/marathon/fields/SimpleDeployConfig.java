package com.mesosphere.velocity.marathon.fields;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Simple Deploy Configuration
 *
 * @author luketornquist
 * @since 1/21/17
 */
public class SimpleDeployConfig extends AbstractDescribableImpl<SimpleDeployConfig> {
    @Extension
    public final static DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    private final String filename;

    @DataBoundConstructor
    public SimpleDeployConfig(final String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleDeployConfig)) return false;

        SimpleDeployConfig that = (SimpleDeployConfig) o;

        return filename.equals(that.filename);

    }

    @Override
    public int hashCode() {
        return filename.hashCode();
    }

    public static class DescriptorImpl extends Descriptor<SimpleDeployConfig> {
        public String getDisplayName() {
            return "Deployment Configuration";
        }
    }
}
