package com.mesosphere.velocity.marathon.fields;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

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

    @Override
    public boolean equals(Object obj) {
        return ((obj instanceof MarathonLabel)
                && ((MarathonLabel) obj).getName().equals(this.getName()));
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MarathonLabel> {
        public String getDisplayName() {
            return "";
        }
    }
}
