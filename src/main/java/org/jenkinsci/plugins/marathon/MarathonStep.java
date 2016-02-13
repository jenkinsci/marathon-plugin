package org.jenkinsci.plugins.marathon;

import hudson.Extension;
import org.jenkinsci.plugins.marathon.fields.MarathonLabel;
import org.jenkinsci.plugins.marathon.fields.MarathonUri;
import org.jenkinsci.plugins.marathon.interfaces.AppConfig;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class MarathonStep extends AbstractStepImpl implements AppConfig {
    private final String              url;
    private       List<MarathonUri>   uris;
    private       List<MarathonLabel> labels;
    private       String              appid;
    private       String              docker;

    @DataBoundConstructor
    public MarathonStep(final String url) {
        this.url = url;
        this.uris = new ArrayList<MarathonUri>(5);
        this.labels = new ArrayList<MarathonLabel>(5);
    }

    @Override
    public String getAppId() {
        return this.appid;
    }

    public String getUrl() {
        return url;
    }

    public String getDocker() {
        return docker;
    }

    @DataBoundSetter
    public void setDocker(String docker) {
        this.docker = docker;
    }

    public List<MarathonUri> getUris() {
        return uris;
    }

    @DataBoundSetter
    public void setUris(List<MarathonUri> uris) {
        this.uris = uris;
    }

    public List<MarathonLabel> getLabels() {
        return labels;
    }

    @DataBoundSetter
    public void setLabels(List<MarathonLabel> labels) {
        this.labels = labels;
    }

    public String getAppid() {
        return appid;
    }

    @DataBoundSetter
    public void setAppid(String appid) {
        this.appid = appid;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(MarathonStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "marathon";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Marathon deployment";
        }
    }
}
