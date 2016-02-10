package org.jenkinsci.plugins.marathon;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by colin on 2/5/16.
 */
public class MarathonLabel extends AbstractDescribableImpl<MarathonLabel> {
    private final String name;
    private final String value;

    @DataBoundConstructor
    public MarathonLabel(final String name, final String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MarathonLabel> {
        public String getDisplayName() {
            return "";
        }
    }

}
