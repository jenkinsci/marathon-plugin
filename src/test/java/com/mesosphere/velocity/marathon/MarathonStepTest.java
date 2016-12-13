package com.mesosphere.velocity.marathon;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class MarathonStepTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Test using "id" and not receiving a deprecation warning message. The job should
     * also fail as the URL is not valid.
     *
     * @throws Exception
     */
    @Test
    public void testStepFail() throws Exception {
        final WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow");

        job.setDefinition(new CpsFlowDefinition("node { marathon(id: 'testStepFail', url: 'http://example.com/'); }", true));
        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        j.assertLogNotContains("DEPRECATION WARNING", run);
    }

    /**
     * Test that using "appid" instead of "id" shows a deprecation warning message.
     *
     * @throws Exception
     */
    @Test
    public void testStepAppIdDeprecationMessage() throws Exception {
        final WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow");

        job.setDefinition(new CpsFlowDefinition("node { marathon(appid: 'testStepAppIdDeprecationMessage', url: 'http://example.com/'); }", true));
        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        j.assertLogContains("DEPRECATION WARNING", run);
    }
}
