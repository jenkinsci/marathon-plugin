package org.jenkinsci.plugins.marathon.fields;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

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
    public boolean equals(Object obj) {
        return ((obj instanceof MarathonUri)
                && ((MarathonUri) obj).getUri().equals(this.getUri()));
    }

    public static class DescriptorImpl extends Descriptor<MarathonUri> {
        public String getDisplayName() {
            return "Mesos URI";
        }
    }
}
