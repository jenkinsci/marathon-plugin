package com.mesosphere.velocity.marathon;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import hudson.ExtensionList;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.Shell;
import hudson.util.Secret;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;
import java.io.OutputStream;
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
    private HttpServer  httpServer;
    private TestHandler handler;

    @Before
    public void setUp() throws IOException {
        handler = new TestHandler();
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", 0);

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
        final FreeStyleBuild build = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        // assert things
        j.assertLogContains("[Marathon]", build);
        j.assertLogContains("marathon.json", build);
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
        final String           payload     = "{\"id\":\"myapp\"}";
        final FreeStyleProject project     = j.createFreeStyleProject();
        final String           responseStr = "{\"version\": \"one\", \"deploymentId\": \"someid-here\"}";
        handler.setResponseBody(responseStr);

        // add builders
        setupBasicProject(payload, project);

        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        // assert things
        j.assertLogContains("application updated", build);
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
                "      \"forcePullImage\": true,\n" +
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
        final String           responseStr = "{\"version\": \"one\", \"deploymentId\": \"someid-here\"}";
        handler.setResponseBody(responseStr);

        // add builders
        setupBasicProject(payload, project);

        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0).get());
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
        setupBasicProject(payload, project);
        // return 409 to trigger retry logic
        handler.setResponseCode(409);
        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        // assert things
        j.assertLogContains("[Marathon]", build);
        j.assertLogContains("max retries", build);
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
        setupBasicProject(payload, project);
        // return a 404, which will fail the build
        handler.setResponseCode(404);
        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        // assert things
        j.assertLogContains("Failed to update", build);
        assertEquals("Only 1 request should be made", 1, handler.getRequestCount());
    }

    /**
     * Test that the URL is properly put through the replace macro and able to be populated with
     * Jenkins variables.
     *
     * @throws Exception
     */
    @Test
    public void testURLMacro() throws Exception {
        final String           payload     = "{\"id\":\"myapp\"}";
        final FreeStyleProject project     = j.createFreeStyleProject();
        final String           responseStr = "{\"version\": \"one\", \"deploymentId\": \"someid-here\"}";
        handler.setResponseBody(responseStr);

        // add builders
        addBuilders(payload, project);
        // add post-builder
        project.getPublishersList().add(new MarathonRecorder(getHttpAddresss() + "/${BUILD_NUMBER}"));

        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        j.assertLogContains("[Marathon]", build);

        assertEquals("Only 1 request should be made", 1, handler.getRequestCount());
        assertEquals("App URL should have build number",
                "/" + String.valueOf(build.getNumber()) + "/v2/apps/myapp",
                handler.getRequests().get(0).getUri().getPath());
    }

    private void setupBasicProject(String payload, FreeStyleProject project) {
        // add builders
        addBuilders(payload, project);
        // add post-builder
        project.getPublishersList().add(new MarathonRecorder(getHttpAddresss()));
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
        setupBasicProject(payload, project);

        // return a 503, which will fail the build
        handler.setResponseCode(503);

        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());

        // assert things
        j.assertLogContains("Failed to update", build);
        assertEquals("Only 1 request should be made", 1, handler.getRequestCount());
    }

    @Test
    public void testBasicToken() throws Exception {
        final String           payload     = "{\"id\":\"myapp\"}";
        final FreeStyleProject project     = j.createFreeStyleProject();
        final String           responseStr = "{\"version\": \"one\", \"deploymentId\": \"someid-here\"}";

        handler.setResponseCode(200);
        handler.setResponseBody(responseStr);

        final SystemCredentialsProvider.ProviderImpl system      = ExtensionList.lookup(CredentialsProvider.class).get(SystemCredentialsProvider.ProviderImpl.class);
        final CredentialsStore                       systemStore = system.getStore(j.getInstance());
        final String                                 tokenValue  = "my secret token";
        final Secret                                 secret      = Secret.fromString(tokenValue);
        final StringCredentials                      credential  = new StringCredentialsImpl(CredentialsScope.GLOBAL, "basictoken", "a token for basic token test", secret);

        systemStore.addCredentials(Domain.global(), credential);

        // add builders
        addBuilders(payload, project);
        // add post-builder
        addPostBuilders(project, "basictoken");

        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        j.assertLogContains("[Marathon]", build);

        // handler assertions
        assertEquals("Only 1 request should be made", 1, handler.getRequestCount());
        final String authorizationText = handler.getRequests().get(0).getHeaders().getFirst("Authorization");
        assertEquals("Token does not match", "token=" + tokenValue, authorizationText);
    }

    /**
     * Test that a JSON credential with "jenkins_token" uses the token value as the authentication token.
     *
     * @throws Exception
     */
    @Test
    public void testJSONToken() throws Exception {
        final String           payload     = "{\"id\":\"myapp\"}";
        final FreeStyleProject project     = j.createFreeStyleProject();
        final String           responseStr = "{\"version\": \"one\", \"deploymentId\": \"someid-here\"}";

        handler.setResponseCode(200);
        handler.setResponseBody(responseStr);

        final SystemCredentialsProvider.ProviderImpl system          = ExtensionList.lookup(CredentialsProvider.class).get(SystemCredentialsProvider.ProviderImpl.class);
        final CredentialsStore                       systemStore     = system.getStore(j.getInstance());
        final String                                 tokenValue      = "my secret token";
        final String                                 credentialValue = "{\"field1\":\"some value\", \"jenkins_token\":\"" + tokenValue + "\"}";
        final Secret                                 secret          = Secret.fromString(credentialValue);
        final StringCredentials                      credential      = new StringCredentialsImpl(CredentialsScope.GLOBAL, "jsontoken", "a token for JSON token test", secret);

        systemStore.addCredentials(Domain.global(), credential);

        // add builders
        addBuilders(payload, project);

        // add post-builder
        addPostBuilders(project, "jsontoken");

        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        j.assertLogContains("[Marathon]", build);

        // handler assertions
        assertEquals("Only 1 request should be made", 1, handler.getRequestCount());
        final String authorizationText = handler.getRequests().get(0).getHeaders().getFirst("Authorization");
        assertEquals("Token does not match", "token=" + tokenValue, authorizationText);
    }

    /**
     * Test that a JSON credential without a "jenkins_token" field and without a proper DC/OS service account value
     * results in a 401 and only 1 web request.
     *
     * @throws Exception
     */
    @Test
    public void testInvalidToken() throws Exception {
        final String           payload = "{\"id\":\"myapp\"}";
        final FreeStyleProject project = j.createFreeStyleProject();

        final SystemCredentialsProvider.ProviderImpl system          = ExtensionList.lookup(CredentialsProvider.class).get(SystemCredentialsProvider.ProviderImpl.class);
        final CredentialsStore                       systemStore     = system.getStore(j.getInstance());
        final String                                 credentialValue = "{\"field1\":\"some value\"}";
        final Secret                                 secret          = Secret.fromString(credentialValue);
        final StringCredentials                      credential      = new StringCredentialsImpl(CredentialsScope.GLOBAL, "invalidtoken", "a token for JSON token test", secret);

        systemStore.addCredentials(Domain.global(), credential);

        handler.setResponseCode(401);
        addBuilders(payload, project);

        // add post-builder
        addPostBuilders(project, "invalidtoken");

        final FreeStyleBuild build = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        j.assertLogContains("[Marathon] Authentication to Marathon instance failed:", build);
        j.assertLogContains("[Marathon] Invalid DC/OS service account JSON", build);
        assertEquals("Only 1 request should have been made.", 1, handler.getRequestCount());
    }

    private void addBuilders(String payload, FreeStyleProject project) {// add builders
        project.getBuildersList().add(new Shell("echo hello"));
        project.getBuildersList().add(createMarathonFileBuilder(payload));
    }

    private void addPostBuilders(FreeStyleProject project, String jsontoken) {
        MarathonRecorder marathonRecorder = new MarathonRecorder(getHttpAddresss());
        marathonRecorder.setCredentialsId(jsontoken);
        project.getPublishersList().add(marathonRecorder);
    }

    private String getHttpAddresss() {
        return "http://" + httpServer.getAddress().getHostName() + ":" + httpServer.getAddress().getPort();
    }

    /**
     * An {@link HttpHandler} that counts the number of requests.
     * The response body and status code can be altered for each
     * test scenario.
     */
    private class TestHandler implements HttpHandler {
        private int               requestCount;
        private String            responseBody;
        private int               responseCode;
        private List<TestRequest> requests;

        TestHandler() {
            this.requestCount = 0;
            this.responseBody = null;
            this.responseCode = 200;
            this.requests = new ArrayList<TestRequest>(5);
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            requestCount++;

            requests.add(new TestRequest(httpExchange.getRequestURI(), IOUtils.toString(httpExchange.getRequestBody()), httpExchange.getRequestHeaders()));
            httpExchange.sendResponseHeaders(responseCode, responseBody != null ? responseBody.length() : 0);
            if (responseBody != null) {
                final OutputStream os = httpExchange.getResponseBody();
                os.write(responseBody.getBytes());
                os.close();
            }
        }

        int getRequestCount() {
            return requestCount;
        }

        public String getResponseBody() {
            return responseBody;
        }

        void setResponseBody(String responseBody) {
            this.responseBody = responseBody;
        }

        public int getResponseCode() {
            return responseCode;
        }

        void setResponseCode(int responseCode) {
            this.responseCode = responseCode;
        }

        public void resetRequestCount() {
            this.requestCount = 0;
        }

        List<TestRequest> getRequests() {
            return requests;
        }
    }

    private class TestRequest {
        private String  body;
        private URI     uri;
        private Headers headers;


        TestRequest(URI uri, String body, Headers headers) {
            this.uri = uri;
            this.body = body;
            this.headers = headers;
        }

        String getBody() {
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

        Headers getHeaders() {
            return headers;
        }
    }
}