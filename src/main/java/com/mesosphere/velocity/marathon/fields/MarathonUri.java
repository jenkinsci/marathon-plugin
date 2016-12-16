package com.mesosphere.velocity.marathon.fields;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Objects;

public class MarathonUri extends AbstractDescribableImpl<MarathonUri> {
    @Extension
    public final static DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    private final String uri;

    @DataBoundConstructor
    public MarathonUri(final String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof MarathonUri)) return false;

        final MarathonUri uri = (MarathonUri) obj;
        return uri.getUri().equals(this.getUri());
    }

    public static class DescriptorImpl extends Descriptor<MarathonUri> {
        public String getDisplayName() {
            return "Mesos URI";
        }
    }
}
