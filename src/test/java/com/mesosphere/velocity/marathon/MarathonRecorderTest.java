package com.mesosphere.velocity.marathon;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.fields.MarathonVars;
import hudson.ExtensionList;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.Shell;
import hudson.util.Secret;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MarathonRecorderTest {
    private final static String      GENERIC_RESPONSE = "{\"version\": \"one\", \"deploymentId\": \"someid-here\"}";
    @Rule
    public               JenkinsRule j                = new JenkinsRule();
    @Rule
    public               TestName    name             = new TestName();

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
        project.getPublishersList().add(new MarathonRecorder(TestUtils.getHttpAddresss(httpServer)));

        // run a build with the shell step and recorder publisher
        final FreeStyleBuild build = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        // assert things
        j.assertLogContains("[Marathon]", build);
        j.assertLogContains("marathon.json", build);
        assertEquals("No web requests were made", 0, httpServer.getRequestCount());
    }

    /**
     * Test a basic successful scenario. The Marathon instance will return
     * a 200 OK.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderPass() throws Exception {
        final FreeStyleProject project = basicSetup(new MarathonRecorder(TestUtils.getHttpAddresss(httpServer)));
        final FreeStyleBuild   build   = basicRunWithSuccess(project);
        // assert things
        j.assertLogContains("application updated", build);
        assertEquals("Only 1 web request", 1, httpServer.getRequestCount());
    }

    /**
     * Test that the payload has all fields supported by Marathon API.
     *
     * @throws Exception
     */
    @Test
    public void testMarathonAllFields() throws Exception {
        final String           payload          = TestUtils.loadFixture("allfields.json");
        final JSONObject       payloadJson      = JSONObject.fromObject(payload);
        final MarathonRecorder marathonRecorder = new MarathonRecorder(TestUtils.getHttpAddresss(httpServer));
        final FreeStyleProject project          = basicSetup(marathonRecorder, payload);
        final FreeStyleBuild   build            = basicRunWithSuccess(project);
        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());

        // get the request body of the first request sent to the handler
        final JSONObject jsonRequest = TestUtils.jsonFromRequest(httpServer);
        assertEquals("JSON does not match", payloadJson, jsonRequest);
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
        // return 409 to trigger retry logic
        TestUtils.enqueueFailureResponse(httpServer, 409);
        TestUtils.enqueueFailureResponse(httpServer, 409);
        TestUtils.enqueueFailureResponse(httpServer, 409);
        // add a few more in case of loop bugs.
        // (this can cause tests to hang if the server is no longer responding)
        TestUtils.enqueueFailureResponse(httpServer, 409);
        TestUtils.enqueueFailureResponse(httpServer, 409);
        TestUtils.enqueueFailureResponse(httpServer, 409);

        final FreeStyleProject project = basicSetup(new MarathonRecorder(TestUtils.getHttpAddresss(httpServer)));
        final FreeStyleBuild   build   = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());

        // assert things
        j.assertLogContains("[Marathon]", build);
        j.assertLogContains("max retries", build);
        assertEquals("Should be 3 retries", 3, httpServer.getRequestCount());
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
        // return a 404, which will fail the build
        TestUtils.enqueueFailureResponse(httpServer, 404);

        final FreeStyleProject project = basicSetup(new MarathonRecorder(TestUtils.getHttpAddresss(httpServer)));
        final FreeStyleBuild   build   = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());

        j.assertLogContains("Failed to update", build);
        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
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
        // return a 503, which will fail the build
        TestUtils.enqueueFailureResponse(httpServer, 503);

        final FreeStyleProject project = basicSetup(new MarathonRecorder(TestUtils.getHttpAddresss(httpServer)));
        final FreeStyleBuild   build   = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());

        // assert things
        j.assertLogContains("Failed to update", build);
        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
    }

    /**
     * Test that the URL is properly put through the replace macro and able to be populated with
     * Jenkins variables.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderURLMacro() throws Exception {
        final MarathonRecorder marathonRecorder = new MarathonRecorder(TestUtils.getHttpAddresss(httpServer) + "${BUILD_NUMBER}");
        final FreeStyleProject project          = basicSetup(marathonRecorder);
        final FreeStyleBuild   build            = basicRunWithSuccess(project);

        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
        RecordedRequest request     = httpServer.takeRequest();
        String          requestPath = request.getPath();
        if (requestPath.contains("?")) {
            requestPath = requestPath.substring(0, requestPath.indexOf("?"));
        }

        assertEquals("App URL should have build number",
                "/" + String.valueOf(build.getNumber()) + "/v2/apps/myapp",
                requestPath);
    }

    /**
     * Test that URIs are properly put through replace macro.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderURIsMacro() throws Exception {
        final List<MarathonUri> uris             = new ArrayList<>(2);
        final MarathonRecorder  marathonRecorder = new MarathonRecorder(TestUtils.getHttpAddresss(httpServer));

        uris.add(new MarathonUri("http://example.com/${BUILD_NUMBER}"));
        uris.add(new MarathonUri("http://again.example.com/$BUILD_NUMBER"));
        marathonRecorder.setUris(uris);

        final FreeStyleProject project = basicSetup(marathonRecorder);
        final FreeStyleBuild   build   = basicRunWithSuccess(project);

        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
        final JSONObject   jsonRequest = TestUtils.jsonFromRequest(httpServer);
        final JSONArray    urisList    = jsonRequest.getJSONArray("uris");
        final String       buildNumber = String.valueOf(build.getNumber());
        final List<String> buildUris   = new ArrayList<>(Arrays.asList("http://example.com/" + buildNumber, "http://again.example.com/" + buildNumber));

        for (Object uriObj : urisList) {
            String uri = (String) uriObj;
            assertTrue("Invalid URI", buildUris.contains(uri));
        }
    }

    /**
     * Test that Labels are properly put through replace macro.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderLabelsMacro() throws Exception {
        final List<MarathonLabel> labels           = new ArrayList<>(2);
        final MarathonRecorder    marathonRecorder = new MarathonRecorder(TestUtils.getHttpAddresss(httpServer));

        labels.add(new MarathonLabel("foo", "bar-${BUILD_NUMBER}"));
        labels.add(new MarathonLabel("fizz", "buzz-$BUILD_NUMBER"));
        marathonRecorder.setLabels(labels);

        final FreeStyleProject project = basicSetup(marathonRecorder);
        final FreeStyleBuild   build   = basicRunWithSuccess(project);

        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
        final JSONObject jsonRequest = TestUtils.jsonFromRequest(httpServer);
        final JSONObject jsonLabel   = jsonRequest.getJSONObject("labels");
        final String     buildNumber = String.valueOf(build.getNumber());

        assertEquals("'foo' label failed", "bar-" + buildNumber, jsonLabel.getString("foo"));
        assertEquals("'fizz1' label failed", "buzz-" + buildNumber, jsonLabel.getString("fizz"));
    }


    /**
     * Test that Vars are properly put through the replace macro.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderEnvMacro() throws Exception {
        final List<MarathonVars> envs             = new ArrayList<>(2);
        final MarathonRecorder   marathonRecorder = new MarathonRecorder(TestUtils.getHttpAddresss(httpServer));

        envs.add(new MarathonVars("foo", "bar-${BUILD_NUMBER}"));
        envs.add(new MarathonVars("fizz", "buzz-$BUILD_NUMBER"));
        marathonRecorder.setEnvironment(envs);

        final FreeStyleProject project = basicSetup(marathonRecorder);
        final FreeStyleBuild   build   = basicRunWithSuccess(project);

        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
        final JSONObject jsonRequest = TestUtils.jsonFromRequest(httpServer);
        final JSONObject jsonEnv     = jsonRequest.getJSONObject("env");
        final String     buildNumber = String.valueOf(build.getNumber());

        assertEquals("'foo' label failed", "bar-" + buildNumber, jsonEnv.getString("foo"));
        assertEquals("'fizz1' label failed", "buzz-" + buildNumber, jsonEnv.getString("fizz"));
    }

    /**
     * Test that appId goes through the replace macro correctly.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderAppIdMacro() throws Exception {
        final MarathonRecorder marathonRecorder = new MarathonRecorder(TestUtils.getHttpAddresss(httpServer));
        marathonRecorder.setAppid("mytestapp-${BUILD_NUMBER}");

        final FreeStyleProject project = basicSetup(marathonRecorder);
        final FreeStyleBuild   build   = basicRunWithSuccess(project);

        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
        final JSONObject jsonRequest = TestUtils.jsonFromRequest(httpServer);
        assertEquals("Wrong ID was submitted",
                "mytestapp-" + String.valueOf(build.getNumber()),
                jsonRequest.getString("id"));
    }

    /**
     * Test the docker image properly goes through replace macro.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderDockerMacro() throws Exception {
        final MarathonRecorder marathonRecorder = new MarathonRecorder(TestUtils.getHttpAddresss(httpServer));
        marathonRecorder.setDocker("image-${BUILD_NUMBER}");

        final FreeStyleProject project = basicSetup(marathonRecorder);
        final FreeStyleBuild   build   = basicRunWithSuccess(project);

        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
        final JSONObject jsonRequest = TestUtils.jsonFromRequest(httpServer);
        assertEquals("Wrong docker image was submitted",
                "image-" + String.valueOf(build.getNumber()),
                jsonRequest.getJSONObject("container").getJSONObject("docker").getString("image"));
    }

    /**
     * Test a basic API token using StringCredentials.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderBasicToken() throws Exception {
        final FreeStyleProject                       project     = j.createFreeStyleProject();
        final String                                 responseStr = "{\"version\": \"one\", \"deploymentId\": \"someid-here\"}";
        final SystemCredentialsProvider.ProviderImpl system      = ExtensionList.lookup(CredentialsProvider.class).get(SystemCredentialsProvider.ProviderImpl.class);
        final CredentialsStore                       systemStore = system.getStore(j.getInstance());
        final String                                 tokenValue  = "my secret token";
        final Secret                                 secret      = Secret.fromString(tokenValue);
        final StringCredentials                      credential  = new StringCredentialsImpl(CredentialsScope.GLOBAL, "basictoken", "a token for basic token test", secret);
        TestUtils.enqueueJsonResponse(httpServer, responseStr);
        systemStore.addCredentials(Domain.global(), credential);

        // add builders
        addBuilders(TestUtils.loadFixture("idonly.json"), project);
        // add post-builder
        addPostBuilders(project, "basictoken");

        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        j.assertLogContains("[Marathon]", build);

        // handler assertions
        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
        RecordedRequest request = httpServer.takeRequest();

        final String authorizationText = request.getHeader("Authorization");
        assertEquals("Token does not match", "token=" + tokenValue, authorizationText);
    }

    /**
     * Test that a JSON credential with "jenkins_token" uses the token value as the authentication token.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderJSONToken() throws Exception {
        final FreeStyleProject                       project         = j.createFreeStyleProject();
        final String                                 responseStr     = "{\"version\": \"one\", \"deploymentId\": \"someid-here\"}";
        final SystemCredentialsProvider.ProviderImpl system          = ExtensionList.lookup(CredentialsProvider.class).get(SystemCredentialsProvider.ProviderImpl.class);
        final CredentialsStore                       systemStore     = system.getStore(j.getInstance());
        final String                                 tokenValue      = "my secret token";
        final String                                 credentialValue = "{\"field1\":\"some value\", \"jenkins_token\":\"" + tokenValue + "\"}";
        final Secret                                 secret          = Secret.fromString(credentialValue);
        final StringCredentials                      credential      = new StringCredentialsImpl(CredentialsScope.GLOBAL, "jsontoken", "a token for JSON token test", secret);
        TestUtils.enqueueJsonResponse(httpServer, responseStr);
        systemStore.addCredentials(Domain.global(), credential);

        // add builders
        addBuilders(TestUtils.loadFixture("idonly.json"), project);

        // add post-builder
        addPostBuilders(project, "jsontoken");

        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        j.assertLogContains("[Marathon]", build);

        // handler assertions
        assertEquals("Only 1 request should be made", 1, httpServer.getRequestCount());
        RecordedRequest request           = httpServer.takeRequest();
        final String    authorizationText = request.getHeader("Authorization");
        assertEquals("Token does not match", "token=" + tokenValue, authorizationText);
    }

    /**
     * Test that a JSON credential without a "jenkins_token" field and without a proper DC/OS service account value
     * results in a 401 and only 1 web request.
     *
     * @throws Exception
     */
    @Test
    public void testRecorderInvalidToken() throws Exception {
        final FreeStyleProject                       project         = j.createFreeStyleProject();
        final SystemCredentialsProvider.ProviderImpl system          = ExtensionList.lookup(CredentialsProvider.class).get(SystemCredentialsProvider.ProviderImpl.class);
        final CredentialsStore                       systemStore     = system.getStore(j.getInstance());
        final String                                 credentialValue = "{\"field1\":\"some value\"}";
        final Secret                                 secret          = Secret.fromString(credentialValue);
        final StringCredentials                      credential      = new StringCredentialsImpl(CredentialsScope.GLOBAL, "invalidtoken", "a token for JSON token test", secret);
        TestUtils.enqueueFailureResponse(httpServer, 401);

        systemStore.addCredentials(Domain.global(), credential);

        addBuilders(TestUtils.loadFixture("idonly.json"), project);

        // add post-builder
        addPostBuilders(project, "invalidtoken");

        final FreeStyleBuild build = j.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        j.assertLogContains("[Marathon] Authentication to Marathon instance failed:", build);
        j.assertLogContains("[Marathon] Invalid DC/OS service account JSON", build);
        assertEquals("Only 1 request should have been made.", 1, httpServer.getRequestCount());
    }

    private void addBuilders(String payload, FreeStyleProject project) {// add builders
        project.getBuildersList().add(new Shell("echo hello"));
        project.getBuildersList().add(createMarathonFileBuilder(payload));
    }

    private void addPostBuilders(FreeStyleProject project, String jsontoken) {
        MarathonRecorder marathonRecorder = new MarathonRecorder(TestUtils.getHttpAddresss(httpServer));
        marathonRecorder.setCredentialsId(jsontoken);
        project.getPublishersList().add(marathonRecorder);
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

    private FreeStyleProject basicSetup(final MarathonRecorder mr) throws IOException {
        return basicSetup(mr, TestUtils.loadFixture("idonly.json"));
    }

    private FreeStyleProject basicSetup(final MarathonRecorder marathonRecorder, final String payload) throws IOException {
        final FreeStyleProject project = j.jenkins.createProject(FreeStyleProject.class, name.getMethodName());

        TestUtils.enqueueJsonResponse(httpServer, GENERIC_RESPONSE);
        addBuilders(payload, project);
        project.getPublishersList().add(marathonRecorder);
        return project;
    }

    private FreeStyleBuild basicRunWithSuccess(final FreeStyleProject project) throws Exception {
        final FreeStyleBuild build = j.assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        j.assertLogContains("[Marathon]", build);
        return build;
    }
}