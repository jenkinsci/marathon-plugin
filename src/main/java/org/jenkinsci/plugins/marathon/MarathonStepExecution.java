package org.jenkinsci.plugins.marathon;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.marathon.util.MarathonBuilderUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.util.logging.Logger;

public class MarathonStepExecution extends AbstractSynchronousStepExecution<Void> {
    private static final Logger LOGGER = Logger.getLogger(MarathonStepExecution.class.getName());

    @StepContextParameter
    private transient TaskListener listener;

    @StepContextParameter
    private transient FilePath ws;

    @StepContextParameter
    private transient Run build;

    @StepContextParameter
    private transient Launcher launcher;

    @StepContextParameter
    private transient EnvVars envVars;

    @Inject
    private transient MarathonStep step;

    @Override
    protected Void run() throws Exception {
        MarathonBuilderUtils.doPerform(ws, envVars, step, LOGGER);
        return null;
    }
}
