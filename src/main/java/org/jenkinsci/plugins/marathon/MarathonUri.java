package org.jenkinsci.plugins.marathon;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by colin on 2/5/16.
 */
public class MarathonUri extends AbstractDescribableImpl<MarathonUri> {
    @Extension
    public final static DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    private final String uri;
    private final boolean executable;
    private final boolean extract;
    private final boolean cache;

    @DataBoundConstructor
    public MarathonUri(final String uri, final boolean executable, final boolean extract, final boolean cache) {
        this.uri = uri;
        this.executable = executable;
        this.extract = extract;
        this.cache = cache;
    }

    public String getUri() {
        return uri;
    }

    public boolean isExecutable() {
        return executable;
    }

    public boolean isExtract() {
        return extract;
    }

    public boolean isCache() {
        return cache;
    }

    public static class DescriptorImpl extends Descriptor<MarathonUri> {
        public String getDisplayName() {
            return "Mesos URI";
        }

    }
}
