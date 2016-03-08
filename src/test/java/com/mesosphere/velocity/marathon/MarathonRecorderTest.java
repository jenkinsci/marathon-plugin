package com.mesosphere.velocity.marathon;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.Shell;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MarathonRecorderTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * An HTTP Server to receive requests from the plugin.
     */
    HttpServer        httpServer;
    TestHandler       handler;
    InetSocketAddress serverAddress;

    @Before
    public void setUp() throws IOException {
        handler = new TestHandler();
        serverAddress = new InetSocketAddress("localhost", 0);

        httpServer = HttpServer.create(serverAddress, 500);
        httpServer.createContext("/", handler);
        httpServer.start();
    }

    @After
    public void tearDown() {
        httpServer.stop(0);
    }

    /**
     * Test that when "marathon.json" is not present the build is failed
     * and no requests are made to the configured Marathon instance.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderNoFile() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new Shell("echo hello"));

        // add recorder
        project.getPublishersList().add(new MarathonRecorder(getHttpAddresss()));

        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = project.scheduleBuild2(0).get();

        // get console log
        final String s = FileUtils.readFileToString(build.getLogFile());

        // assert things
        assertEquals("Build should fail", Result.FAILURE, build.getResult());
        assertTrue(s.contains("[Marathon]"));
        assertTrue(s.contains("marathon.json"));
        assertEquals("No web requests were made", 0, handler.getRequestCount());
    }

    /**
     * Test a basic successful scenario. The Marathon instance will return
     * a 200 OK.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderPass() throws Exception {
        final String           payload = "{\"id\":\"myapp\"}";
        final FreeStyleProject project = j.createFreeStyleProject();

        // add builders
        project.getBuildersList().add(new Shell("echo hello"));
        project.getBuildersList().add(createMarathonFileBuilder(payload));

        // add post-builder
        project.getPublishersList().add(new MarathonRecorder(getHttpAddresss()));

        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = project.scheduleBuild2(0).get();

        // get console log
        final String s = FileUtils.readFileToString(build.getLogFile());

        // assert things
        assertEquals("Build should fail", Result.SUCCESS, build.getResult());
        assertTrue(s.contains("[Marathon]"));
        assertTrue(s.contains("application updated"));
        assertEquals("Only 1 web request", 1, handler.getRequestCount());
    }

    private TestBuilder createMarathonFileBuilder(final String payload) {
        return new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                                   BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("marathon.json").write(payload, "UTF-8");
                return true;
            }
        };
    }

    /**
     * Test that the payload has all fields supported by Marathon API.
     *
     * @throws Exception
     */
    @Test
    public void testMarathonAllFields() throws Exception {
        final String payload = "{\n" +
                "  \"id\": \"test-app\",\n" +
                "  \"container\": {\n" +
                "    \"type\": \"DOCKER\",\n" +
                "    \"docker\": {\n" +
                "      \"image\": \"mesosphere/test-app:latest\",\n" +
                "      \"network\": \"BRIDGE\",\n" +
                "      \"portMappings\": [\n" +
                "        {\n" +
                "          \"hostPort\": 80,\n" +
                "          \"containerPort\": 80,\n" +
                "          \"protocol\": \"tcp\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"acceptedResourceRoles\": [\n" +
                "    \"agent_public\"\n" +
                "  ],\n" +
                "  \"labels\": {\n" +
                "    \"lastChangedBy\": \"test@example.com\"\n" +
                "  },\n" +
                "  \"uris\": [ \"http://www.example.com/file\" ],\n" +
                "  \"instances\": 1,\n" +
                "  \"cpus\": 0.1,\n" +
                "  \"mem\": 128,\n" +
                "  \"healthChecks\": [\n" +
                "    {\n" +
                "      \"protocol\": \"TCP\",\n" +
                "      \"gracePeriodSeconds\": 600,\n" +
                "      \"intervalSeconds\": 30,\n" +
                "      \"portIndex\": 0,\n" +
                "      \"timeoutSeconds\": 10,\n" +
                "      \"maxConsecutiveFailures\": 2\n" +
                "    }\n" +
                "  ],\n" +
                "  \"upgradeStrategy\": {\n" +
                "        \"minimumHealthCapacity\": 0\n" +
                "  },\n" +
                "  \"backoffSeconds\": 1,\n" +
                "  \"backoffFactor\": 1.15,\n" +
                "  \"maxLaunchDelaySeconds\": 3600,\n" +
                "}";
        final JSONObject       payloadJson = JSONObject.fromObject(payload);
        final FreeStyleProject project     = j.createFreeStyleProject();

        // add builders
        project.getBuildersList().add(new Shell("echo hello"));
        project.getBuildersList().add(createMarathonFileBuilder(payload));

        // add post-builder
        project.getPublishersList().add(new MarathonRecorder(getHttpAddresss()));

        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = project.scheduleBuild2(0).get();

        // get console log
        final String s = FileUtils.readFileToString(build.getLogFile());

        // assert things
        assertEquals("Build should fail", Result.SUCCESS, build.getResult());
        assertEquals("Only 1 request should be made", 1, handler.getRequestCount());

        // get the request body of the first request sent to the handler
        final JSONObject requestJson = JSONObject.fromObject(handler.getRequests().get(0).getBody());

        // verify that each root field is present in the received request
        for (Object key : payloadJson.keySet()) {
            assertTrue("JSON is missing field: " + key, requestJson.containsKey(key));
        }
    }

    /**
     * Test that a 409 response from the Marathon instance triggers
     * retry logic. The default logic is to try 3 times with X
     * seconds in between each request.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderMaxRetries() throws Exception {
        final String           payload = "{\"id\":\"myapp\"}";
        final FreeStyleProject project = j.createFreeStyleProject();

        // add builders
        project.getBuildersList().add(new Shell("echo hello"));
        project.getBuildersList().add(createMarathonFileBuilder(payload));

        // add post-builder
        project.getPublishersList().add(new MarathonRecorder(getHttpAddresss()));

        // return 409 to trigger retry logic
        handler.setResponseCode(409);

        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = project.scheduleBuild2(0).get();

        // get console log
        final String s = FileUtils.readFileToString(build.getLogFile());

        // assert things
        assertEquals("Build should fail", Result.FAILURE, build.getResult());
        assertTrue(s.contains("[Marathon]"));
        assertTrue(s.contains("max retries"));
        assertEquals("Should be 3 retries", 3, handler.getRequestCount());
    }

    /**
     * Test that a 4xx (404 in this case) response code does not
     * trigger retries. This should result in only one request
     * being made to the configured Marathon instance.
     *
     * @throws Exception
     */
    @Test
    public void testRecorder404() throws Exception {
        final String           payload = "{\"id\":\"myapp\"}";
        final FreeStyleProject project = j.createFreeStyleProject();

        // add builders
        project.getBuildersList().add(new Shell("echo hello"));
        project.getBuildersList().add(createMarathonFileBuilder(payload));

        // add post-builder
        project.getPublishersList().add(new MarathonRecorder(getHttpAddresss()));

        // return a 404, which will fail the build
        handler.setResponseCode(404);

        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = project.scheduleBuild2(0).get();

        // get console log
        final String s = FileUtils.readFileToString(build.getLogFile());

        // assert things
        assertEquals("Build should fail", Result.FAILURE, build.getResult());
        assertTrue(s.contains("[Marathon]"));
        assertTrue(s.contains("Failed to update"));
        assertEquals("Only 1 request should be made", 1, handler.getRequestCount());
    }

    /**
     * Test that a 5xx (503 in this case) response code does not
     * trigger retries. This should result in only one request
     * being made to the configured Marathon instance.
     *
     * @throws Exception
     */
    @Test
    public void testRecorder503() throws Exception {
        final String           payload = "{\"id\":\"myapp\"}";
        final FreeStyleProject project = j.createFreeStyleProject();

        // add builders
        project.getBuildersList().add(new Shell("echo hello"));
        project.getBuildersList().add(createMarathonFileBuilder(payload));

        // add post-builder
        project.getPublishersList().add(new MarathonRecorder(getHttpAddresss()));

        // return a 503, which will fail the build
        handler.setResponseCode(503);

        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = project.scheduleBuild2(0).get();

        // get console log
        final String s = FileUtils.readFileToString(build.getLogFile());

        // assert things
        assertEquals("Build should fail", Result.FAILURE, build.getResult());
        assertTrue(s.contains("[Marathon]"));
        assertTrue(s.contains("Failed to update"));
        assertEquals("Only 1 request should be made", 1, handler.getRequestCount());
    }

    private String getHttpAddresss() {
        return "http://" + httpServer.getAddress().getHostName() + ":" + httpServer.getAddress().getPort();
    }

    /**
     * An {@link HttpHandler} that counts the number of requests.
     * The response body and status code can be altered for each
     * test scenario.
     */
    class TestHandler implements HttpHandler {
        private int               requestCount;
        private String            responseBody;
        private int               responseCode;
        private List<TestRequest> requests;

        public TestHandler() {
            this.requestCount = 0;
            this.responseBody = null;
            this.responseCode = 200;
            this.requests = new ArrayList<TestRequest>(5);
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            requestCount++;

            requests.add(new TestRequest(httpExchange.getRequestURI(), IOUtils.toString(httpExchange.getRequestBody())));
            httpExchange.sendResponseHeaders(responseCode, responseBody != null ? responseBody.length() : 0);
        }

        public int getRequestCount() {
            return requestCount;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public void setResponseBody(String responseBody) {
            this.responseBody = responseBody;
        }

        public int getResponseCode() {
            return responseCode;
        }

        public void setResponseCode(int responseCode) {
            this.responseCode = responseCode;
        }

        public void resetRequestCount() {
            this.requestCount = 0;
        }

        public List<TestRequest> getRequests() {
            return requests;
        }
    }

    class TestRequest {
        private String body;
        private URI    uri;


        public TestRequest(URI uri, String body) {
            this.uri = uri;
            this.body = body;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public URI getUri() {
            return uri;
        }

        public void setUri(URI uri) {
            this.uri = uri;
        }
    }
}