package com.mesosphere.velocity.marathon.fields;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Objects;

public class MarathonVars extends AbstractDescribableImpl<MarathonVars> {
    private final String name;
    private final String value;

    @DataBoundConstructor
    public MarathonVars(final String name, final String value) {
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
        if (!(obj instanceof MarathonVars)) return false;

        final MarathonVars var = (MarathonVars) obj;
        return var.getName().equals(this.getName()) &&
                var.getValue().equals(this.getValue());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MarathonVars> {
        public String getDisplayName() {
            return "";
        }
    }
}
