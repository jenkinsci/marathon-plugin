package com.mesosphere.velocity.marathon.fields;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Objects;

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
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof MarathonLabel)) return false;

        final MarathonLabel label = (MarathonLabel) obj;
        return label.getName().equals(this.getName()) &&
                label.getValue().equals(this.getValue());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MarathonLabel> {
        public String getDisplayName() {
            return "";
        }
    }
}
