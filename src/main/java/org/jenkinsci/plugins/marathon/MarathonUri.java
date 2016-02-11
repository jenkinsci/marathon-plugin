package org.jenkinsci.plugins.marathon;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by colin on 2/5/16.
 */
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

    public static class DescriptorImpl extends Descriptor<MarathonUri> {
        public String getDisplayName() {
            return "Mesos URI";
        }
    }
}
