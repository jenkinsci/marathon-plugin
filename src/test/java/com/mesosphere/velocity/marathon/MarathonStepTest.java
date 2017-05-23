package com.mesosphere.velocity.marathon;

import hudson.model.Result;
import net.sf.json.JSONObject;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class MarathonStepTest {
    @Rule
    public JenkinsRule j    = new JenkinsRule();
    @Rule
    public TestName    name = new TestName();

    /**
     * An HTTP Server to receive requests from the plugin.
     */
    private MockWebServer httpServer;

    @Before
    public void setUp() throws IOException {
        httpServer = new MockWebServer();
        httpServer.start();
    }

    @After
    public void tearDown() throws IOException {
        httpServer.shutdown();
        httpServer = null;
    }

    /**
     * Test using "id" and not receiving a deprecation warning message.
     *
     * @throws Exception in case something unexpected happens
     */
    @Test
    public void testStepFail() throws Exception {
        TestUtils.enqueueFailureResponse(httpServer, 404);
        final WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, name.getMethodName());

        job.setDefinition(new CpsFlowDefinition(generateSimpleScript(null, name.getMethodName()), true));
        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(1).get());
        j.assertLogNotContains("DEPRECATION WARNING", run);
        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
    }

    /**
     * Test that using "appid" instead of "id" shows a deprecation warning message.
     *
     * @throws Exception in case something unexpected happens
     */
    @Test
    public void testStepAppIdDeprecationMessage() throws Exception {
        TestUtils.enqueueFailureResponse(httpServer, 404);
        final WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, name.getMethodName());

        final String groovyScript = "node { " +
                "writeFile(encoding: 'utf-8', file: 'marathon.json', text: '''" + TestUtils.loadFixture("idonly.json") + "''');\n" +
                "marathon(appid: 'testStepAppIdDeprecationMessage', url: '" + TestUtils.getHttpAddresss(httpServer) + "'); " +
                "}";

        job.setDefinition(new CpsFlowDefinition(groovyScript, true));
        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(1).get());
        j.assertLogContains("DEPRECATION WARNING", run);
        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
    }

    @Test
    public void testStepMultipleDeployments() throws Exception {
        TestUtils.enqueueJsonResponse(httpServer, "{\"version\": \"one\", \"deploymentId\": \"myapp\"}");
        TestUtils.enqueueJsonResponse(httpServer, "{\"version\": \"one\", \"deploymentId\": \"myapp2\"}");

        final WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, name.getMethodName());

        final String groovyScript = "node { " +
                "writeFile(encoding: 'utf-8', file: 'marathon.json', text: '{\"id\": \"testing1\", \"cmd\": \"sleep 60\"}');\n" +
                "writeFile(encoding: 'utf-8', file: 'marathon2.json', text: '{\"id\": \"testing2\", \"cmd\": \"sleep 60\"}');\n" +
                "marathon(url: '" + TestUtils.getHttpAddresss(httpServer) + "'); " +
                "marathon(filename: 'marathon2.json', url: '" + TestUtils.getHttpAddresss(httpServer) + "'); " +
                "}";

        job.setDefinition(new CpsFlowDefinition(groovyScript, true));
        WorkflowRun run = j.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(1).get());
        assertEquals("Two requests should be made", 2, httpServer.getRequestCount());

        final JSONObject request1 = TestUtils.jsonFromRequest(httpServer);
        final JSONObject request2 = TestUtils.jsonFromRequest(httpServer);
        assertEquals("testing1", request1.getString("id"));
        assertEquals("testing2", request2.getString("id"));
    }

    /**
     * Test that 409 triggers retries.
     *
     * @throws Exception if something goes wrong
     */
    @Test
    public void testStepMaxRetries() throws Exception {
        TestUtils.enqueueFailureResponse(httpServer, 409);
        TestUtils.enqueueFailureResponse(httpServer, 409);
        TestUtils.enqueueFailureResponse(httpServer, 409);

        final WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, name.getMethodName());

        job.setDefinition(new CpsFlowDefinition(generateSimpleScript(), true));
        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(1).get());
        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
        j.assertLogContains("Client Error", run);
        j.assertLogContains("http status: 409", run);
    }

    /**
     * Test that when "marathon.json" is not present the build is failed
     * and no requests are made to the configured marathon instance.
     *
     * @throws Exception when errors occur.
     */
    @Test
    public void testStepNoFile() throws Exception {
        TestUtils.enqueueFailureResponse(httpServer, 400);

        final WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "recordernofile");
        job.setDefinition(new CpsFlowDefinition("node {" +
                "marathon(id: '" + name.getMethodName() +
                "', url: '" + TestUtils.getHttpAddresss(httpServer) + "'); " +
                "}"));
        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(1).get());
        j.assertLogContains("Could not find file 'marathon.json'", run);
        assertEquals("No requests were made", 0, httpServer.getRequestCount());
    }

    /**
     * Test a basic successful scenario. The marathon instance will return
     * a 200 OK.
     *
     * @throws Exception if things go awry
     */
    @Test
    public void testStepPass() throws Exception {
        final WorkflowJob job      = j.jenkins.createProject(WorkflowJob.class, name.getMethodName());
        final String      response = "{\"version\": \"one\", \"deploymentId\": \"myapp\"}";
        TestUtils.enqueueJsonResponse(httpServer, response);

        job.setDefinition(new CpsFlowDefinition(generateSimpleScript(), true));
        j.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(1).get());
        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
        assertEquals("Id was not set correctly", "myapp", TestUtils.jsonFromRequest(httpServer).getString("id"));
    }

    /**
     * Test various marathon fields and confirm what was in marathon.json is what
     * was received by the marathon instance.
     *
     * @throws Exception when problems happen
     */
    @Test
    public void testStepAllFields() throws Exception {
        final String payload     = TestUtils.loadFixture("allfields.json");
        final String responseStr = "{\"version\": \"one\", \"deploymentId\": \"someid-here\"}";
        TestUtils.enqueueJsonResponse(httpServer, responseStr);

        final WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, name.getMethodName());
        job.setDefinition(new CpsFlowDefinition(generateSimpleScript(payload, null), true));
        j.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(1).get());
        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());

        JSONObject json = TestUtils.jsonFromRequest(httpServer);
        assertEquals("Id was not set correctly", "/foo", json.getString("id"));

        final JSONObject jsonPayload = JSONObject.fromObject(payload);
        assertEquals("JSON objects are not the same", json, jsonPayload);
    }

    /**
     * Test that the URL is properly put through the replace macro and able to be populated with
     * Jenkins variables.
     *
     * @throws Exception in some special instances
     */
    @Test
    public void testStepURLMacro() throws Exception {
        final String responseStr = "{\"version\": \"one\", \"deploymentId\": \"someid-here\"}";
        TestUtils.enqueueJsonResponse(httpServer, responseStr);

        final WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, name.getMethodName());

        final String groovyScript = "node { " +
                "writeFile(encoding: 'utf-8', file: 'marathon.json', text: '{\"id\": \"myapp\", \"cmd\": \"sleep 60\"}');\n" +
                "marathon(url: '" + TestUtils.getHttpAddresss(httpServer) + "${BUILD_NUMBER}'); " +
                "}";

        job.setDefinition(new CpsFlowDefinition(groovyScript, true));
        WorkflowRun run = j.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(1).get());

        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
        RecordedRequest request     = httpServer.takeRequest();
        String          requestPath = request.getPath();
        if (requestPath.contains("?")) {
            requestPath = requestPath.substring(0, requestPath.indexOf("?"));
        }

        assertEquals("App URL should have build number",
                "/" + String.valueOf(run.getNumber()) + "/v2/apps/myapp",
                requestPath);
    }

    /**
     * See {@link #generateSimpleScript(String, String)} for details.
     *
     * @return pipeline groovy script
     */
    private String generateSimpleScript() throws IOException {
        return generateSimpleScript(null, null);
    }

    /**
     * Helper method to generate the groovy script for pipeline jobs.
     *
     * @param fileContents JSON contents for marathon.json file (optional)
     * @param id           marathon id for application (optional)
     * @return pipeline groovy script
     */
    private String generateSimpleScript(final String fileContents, final String id) throws IOException {
        final String nodeScript = "node { \n" +
                "writeFile(encoding: 'utf-8', file: 'marathon.json', text: \"\"\"%s\"\"\");\n" +
                "marathon(%s url: '%s');\n" +
                "}";

        final String contents = fileContents == null ? TestUtils.loadFixture("idonly.json") : fileContents;
        final String idStr    = id == null ? "" : "id: '" + id + "', ";
        return String.format(nodeScript, contents, idStr, TestUtils.getHttpAddresss(httpServer));
    }
}
