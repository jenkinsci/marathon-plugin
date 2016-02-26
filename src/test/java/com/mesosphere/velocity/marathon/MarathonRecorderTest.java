package com.mesosphere.velocity.marathon;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.Shell;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;

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
        private int    requestCount;
        private String responseBody;
        private int    responseCode;

        public TestHandler() {
            this.requestCount = 0;
            this.responseBody = null;
            this.responseCode = 200;
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            requestCount++;
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
    }
}