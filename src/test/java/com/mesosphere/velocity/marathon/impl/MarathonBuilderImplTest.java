package com.mesosphere.velocity.marathon.impl;

import com.google.gson.JsonSyntaxException;
import com.mesosphere.velocity.marathon.TestUtils;
import com.mesosphere.velocity.marathon.exceptions.AuthenticationException;
import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.fields.MarathonVars;
import com.mesosphere.velocity.marathon.interfaces.AppConfig;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import net.sf.json.JSONObject;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MarathonBuilderImplTest {
    private MockWebServer httpServer;

    @Before
    public void setUp() throws IOException {
        this.httpServer = new MockWebServer();
        this.httpServer.start();
    }

    @After
    public void tearDown() throws IOException {
        this.httpServer.shutdown();
        this.httpServer = null;
    }

    /**
     * Test that the content of the allfields fixture is properly sent to the remote server.
     *
     * @throws IOException             when IO issues occur
     * @throws AuthenticationException when authentication fails
     * @throws InterruptedException    when retries are interrupted
     */
    @Test
    public void testAllMarathonFields() throws IOException, AuthenticationException, InterruptedException {
        MockConfig config  = new MockConfig();
        String     payload = TestUtils.loadFixture("allfields.json");
        JSONObject json    = JSONObject.fromObject(payload);
        config.url = TestUtils.getHttpAddresss(this.httpServer);

        TestUtils.enqueueJsonResponse(this.httpServer, "{}");
        new MarathonBuilderImpl(config)
                .setJson(json)
                .build()
                .update();
        JSONObject jsonRequest = TestUtils.jsonFromRequest(this.httpServer);
        assertEquals("JSON objects are different", json, jsonRequest);

        // secrets
        JSONObject secrets = jsonRequest.getJSONObject("secrets");
        JSONObject secret3 = secrets.getJSONObject("secret3");
        assertEquals("Wrong source for secret3", "/foo2", secret3.getString("source"));

        // secrets in env
        JSONObject env = jsonRequest.getJSONObject("env");
        String actualPassword = env.getJSONObject("PASSWORD").getString("secret");
        String actualXPS2 = env.getString("XPS2");
        assertEquals("Invalid value for PASSWORD", "/db/password", actualPassword);
        assertEquals("Invalid value for XPS2", "Rest", actualXPS2);
    }

    /**
     * Test that a JSON configuration without any URIs does not throw an error.
     */
    @Test
    public void testNoUris() throws IOException {
        String jsonString = TestUtils.loadFixture("idonly.json");
        JSONObject json = JSONObject.fromObject(jsonString);
        MockConfig config = new MockConfig();

        MarathonBuilder builder = new MarathonBuilderImpl(config).setJson(json).build();
        assertNull("URIs should be null if none were in the JSON config", builder.getApp().getUris());
    }

    /**
     * Test that existing URIs are not deleted or overwritten on subsequence builds.
     */
    @Test
    public void testExistingUris() throws IOException {
        String jsonString = TestUtils.loadFixture("uris.json");
        JSONObject json = JSONObject.fromObject(jsonString);

        MarathonBuilder builder = new MarathonBuilderImpl(new MockConfig()).setJson(json).build();
        assertEquals(2, builder.getJson().getJSONArray("uris").size());
        assertEquals(2, builder.getApp().getUris().size());
        assertEquals("https://foo.com/setup.py", builder.getApp().getUris().iterator().next());
    }

    /**
     * Test that an invalid "uris" format causes a JSON exception. The "uris" field should
     * be an array.
     */
    @Test
    public void testInvalidTypeUris() {
        String jsonString = "{\"id\": \"testid\", \"uris\": \"http://example.com/artifact\"}";
        JSONObject json = JSONObject.fromObject(jsonString);
        MockConfig config = new MockConfig();

        try {
            new MarathonBuilderImpl(config).setJson(json).build();
            assertTrue("Should throw json parse exception", false);
        } catch (JsonSyntaxException jse) {
            assertTrue(true);
        }
    }

    /**
     * Test that existing "env" section is not deleted or overwritten on subsequence builds.
     */
    @Test
    public void testExistingEnv() throws IOException {
        String jsonString = TestUtils.loadFixture("env.json");
        JSONObject json = JSONObject.fromObject(jsonString);
        MockConfig config = new MockConfig();

        // add to the env
        config.env.add(new MarathonVars("example", "test"));
        // build
        MarathonBuilder builder = new MarathonBuilderImpl(config).setJson(json).build();

        assertEquals("bar", builder.getApp().getEnv().get("foo"));
        assertEquals("buzz", builder.getApp().getEnv().get("fizz"));
        assertEquals("test", builder.getApp().getEnv().get("example"));
    }

    /**
     * Test that an empty env section can be added to without issues.
     */
    @Test
    public void testNoEnv() throws IOException {
        String jsonString = TestUtils.loadFixture("idonly.json");
        JSONObject json = JSONObject.fromObject(jsonString);
        MockConfig config = new MockConfig();
        MarathonBuilder  builder    = new MarathonBuilderImpl(config).setJson(json).build();
        assertNull("Env should be null", builder.getApp().getEnv());

        config.env.add(new MarathonVars("foo", "bar"));
        builder = builder.build();
        assertEquals("foo not set correctly", "bar", builder.getApp().getEnv().get("foo"));
    }


    static class MockConfig implements AppConfig {
        String              url;
        String              appId;
        boolean             forceUpdate;
        String              docker;
        boolean             dockerForcePull;
        String              credentialsId;
        List<MarathonUri>   uris;
        List<MarathonLabel> labels;
        List<MarathonVars>  env;
        long timeout;

        MockConfig() {
            this.uris = new ArrayList<>();
            this.labels = new ArrayList<>();
            this.env = new ArrayList<>();
        }

        @Override
        public String getAppId() {
            return this.appId;
        }

        @Override
        public String getUrl() {
            return this.url;
        }

        @Override
        public boolean getForceUpdate() {
            return this.forceUpdate;
        }

        @Override
        public String getDocker() {
            return this.docker;
        }

        @Override
        public boolean getDockerForcePull() {
            return this.dockerForcePull;
        }

        @Override
        public String getCredentialsId() {
            return this.credentialsId;
        }

        @Override
        public List<MarathonUri> getUris() {
            return this.uris;
        }

        @Override
        public List<MarathonLabel> getLabels() {
            return this.labels;
        }

        @Override
        public List<MarathonVars> getEnv() {
            return this.env;
        }

        @Override
        public long getTimeout() {
            return this.timeout;
        }
    }
}
