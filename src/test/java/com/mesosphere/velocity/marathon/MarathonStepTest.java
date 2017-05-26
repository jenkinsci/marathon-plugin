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
    private static final String      GENERIC_RESPONSE = "{\"version\": \"one\", \"deploymentId\": \"someid-here\"}";
    @Rule
    public               JenkinsRule j                = new JenkinsRule();
    @Rule
    public               TestName    name             = new TestName();

    /**
     * An HTTP Server to receive requests from the plugin.
     */
    private MockWebServer httpServer;

    private static String marathonLine(final String url) {
        return "url: '" + url + "'";
    }

    private static String marathonLine(final String url, final String id) {
        if (id == null) return marathonLine(url);
        return String.format("id: '%s', url: '%s'", id, url);
    }

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
        final String      script = generateSimpleScript(null, name.getMethodName());
        final WorkflowJob job    = basicSetupWithScript(script);
        final WorkflowRun run    = basicRunWithFailure(job);
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
        final String groovyScript = "node { " +
                "writeFile(encoding: 'utf-8', file: 'marathon.json', text: '''%s''');\n" +
                "marathon(appid: 'testStepAppIdDeprecationMessage', url: '%s'); " +
                "}";

        TestUtils.enqueueFailureResponse(httpServer, 404);
        final String      url     = TestUtils.getHttpAddresss(httpServer);
        final String      payload = TestUtils.loadFixture("idonly.json");
        final String      script  = String.format(groovyScript, payload, url);
        final WorkflowJob job     = basicSetupWithScript(script);
        final WorkflowRun run     = basicRunWithFailure(job);
        j.assertLogContains("DEPRECATION WARNING", run);
        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
    }

    @Test
    public void testStepMultipleDeployments() throws Exception {
        final String groovyScript = "node { " +
                "writeFile(encoding: 'utf-8', file: 'marathon.json', text: '{\"id\": \"testing1\"}');\n" +
                "writeFile(encoding: 'utf-8', file: 'marathon2.json', text: '{\"id\": \"testing2\"}');\n" +
                "marathon(url: '%s'); " +
                "marathon(filename: 'marathon2.json', url: '%s'); " +
                "}";

        // enqueue responses
        TestUtils.enqueueJsonResponse(httpServer, GENERIC_RESPONSE);
        TestUtils.enqueueJsonResponse(httpServer, GENERIC_RESPONSE);

        final String      url            = TestUtils.getHttpAddresss(httpServer);
        final String      workflowScript = String.format(groovyScript, url, url);
        final WorkflowJob job            = basicSetupWithScript(workflowScript);
        basicRunWithSuccess(job);

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

        final WorkflowJob job = basicSetup();
        final WorkflowRun run = basicRunWithFailure(job);
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
        final String groovyScript = "node { marathon(url: '%s'); }";

        TestUtils.enqueueFailureResponse(httpServer, 400);
        final String      url    = TestUtils.getHttpAddresss(httpServer);
        final String      script = String.format(groovyScript, url);
        final WorkflowJob job    = basicSetupWithScript(script);
        final WorkflowRun run    = basicRunWithFailure(job);
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
        basicRunWithSuccess(basicSetup());
        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());

        final String actualId = TestUtils.jsonFromRequest(httpServer).getString("id");
        assertEquals("Id was not set correctly", "myapp", actualId);
    }

    /**
     * Test various marathon fields and confirm what was in marathon.json is what
     * was received by the marathon instance.
     *
     * @throws Exception when problems happen
     */
    @Test
    public void testStepAllFields() throws Exception {
        final String      payload = TestUtils.loadFixture("allfields.json");
        final WorkflowJob job     = basicSetup(payload);
        basicRunWithSuccess(job);

        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
        final JSONObject actualJson   = TestUtils.jsonFromRequest(httpServer);
        final JSONObject expectedJson = JSONObject.fromObject(payload);
        assertEquals("Id was not set correctly", "/foo", actualJson.getString("id"));
        assertEquals("JSON objects are not the same", expectedJson, actualJson);
    }

    /**
     * Test that the URL is properly put through the replace macro and able to be populated with
     * Jenkins variables.
     *
     * @throws Exception in some special instances
     */
    @Test
    public void testStepURLMacro() throws Exception {
        final String groovyScript = "node { " +
                "writeFile(encoding: 'utf-8', file: 'marathon.json', text: '{\"id\": \"myapp\"}');\n" +
                "marathon(url: '%s'); " +
                "}";

        TestUtils.enqueueJsonResponse(httpServer, GENERIC_RESPONSE);
        final String      url    = TestUtils.getHttpAddresss(httpServer) + "${BUILD_NUMBER}";
        final String      script = String.format(groovyScript, url);
        final WorkflowJob job    = basicSetupWithScript(script);
        final WorkflowRun run    = basicRunWithSuccess(job);

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
     * Helper method to generate the groovy script for pipeline jobs.
     *
     * @param fileContents JSON contents for marathon.json file (optional)
     * @param id           marathon id for application (optional)
     * @return pipeline groovy script
     */
    private String generateSimpleScript(final String fileContents, final String id) throws IOException {
        final String nodeScript = "node { \n" +
                "writeFile(encoding: 'utf-8', file: 'marathon.json', text: \"\"\"%s\"\"\");\n" +
                "marathon(%s);\n" +
                "}";

        final String contents     = fileContents == null ? TestUtils.loadFixture("idonly.json") : fileContents;
        final String marathonCall = marathonLine(TestUtils.getHttpAddresss(httpServer), id);
        return String.format(nodeScript, contents, marathonCall);
    }

    private WorkflowJob basicSetup() throws IOException {
        return basicSetup(null);
    }

    /**
     * Basic workflow job setup. This enqueues a JSON response to the
     * mock web server and creates a new workflow job. The new job
     * contains a generic groovy script with the marathon app
     * definition set to payload.
     *
     * @param payload marathon app definition
     * @return created job
     * @throws IOException when filesystem operations have problems
     */
    private WorkflowJob basicSetup(final String payload) throws IOException {
        TestUtils.enqueueJsonResponse(httpServer, GENERIC_RESPONSE);

        final WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, name.getMethodName());
        job.setDefinition(new CpsFlowDefinition(generateSimpleScript(payload, null), true));
        return job;
    }

    /**
     * Basic workflow job setup. This does not enqueue a response to
     * the mock web server.
     *
     * @param script workflow / groovy script
     * @return created job
     * @throws IOException when filesystem has issues
     */
    private WorkflowJob basicSetupWithScript(final String script) throws IOException {
        final WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, name.getMethodName());
        job.setDefinition(new CpsFlowDefinition(script, true));
        return job;
    }

    /**
     * Execute a workflow job, verify it was successful, and check the log contains "marathon".
     *
     * @param project workflow job
     * @return workflow run
     * @throws Exception when a run is unable to be retrieved
     */
    private WorkflowRun basicRunWithSuccess(final WorkflowJob project) throws Exception {
        return basicRunWithResult(project, Result.SUCCESS);
    }

    /**
     * Execute a workflow job and verify it failed.
     *
     * @param project workflow job
     * @return workflow run
     * @throws Exception when a run is unable to be retrieved
     */
    private WorkflowRun basicRunWithFailure(final WorkflowJob project) throws Exception {
        return basicRunWithResult(project, Result.FAILURE);
    }

    /**
     * Execute a run and verify it matches the expected result. This
     * does check the log file upon success for "marathon".
     *
     * @param project workflow job
     * @param result  expected result
     * @return workflow run
     * @throws Exception when a run is unable to be executed
     */
    private WorkflowRun basicRunWithResult(final WorkflowJob project, final Result result) throws Exception {
        final WorkflowRun build = j.assertBuildStatus(result, project.scheduleBuild2(0).get());
        if (result == Result.SUCCESS) j.assertLogContains("marathon", build);
        return build;
    }

}
