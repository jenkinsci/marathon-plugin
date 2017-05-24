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
        httpServer = new MockWebServer();
        httpServer.start();
    }

    @After
    public void tearDown() throws IOException {
        httpServer.shutdown();
        httpServer = null;
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
        config.url = TestUtils.getHttpAddresss(httpServer);

        TestUtils.enqueueJsonResponse(httpServer, "{}");
        new MarathonBuilderImpl(config)
                .setJson(json)
                .build()
                .update();
        final JSONObject jsonRequest = TestUtils.jsonFromRequest(httpServer);
        assertEquals("JSON objects are different", json, jsonRequest);

        // secrets
        final JSONObject secrets = jsonRequest.getJSONObject("secrets");
        final JSONObject secret3 = secrets.getJSONObject("secret3");
        assertEquals("Wrong source for secret3", "/foo2", secret3.getString("source"));

        // secrets in env
        final JSONObject env            = jsonRequest.getJSONObject("env");
        final String     actualPassword = env.getJSONObject("PASSWORD").getString("secret");
        final String     actualXPS2     = env.getString("XPS2");
        assertEquals("Invalid value for PASSWORD", "/db/password", actualPassword);
        assertEquals("Invalid value for XPS2", "Rest", actualXPS2);
    }

    /**
     * Test that a JSON configuration without any URIs does not throw an error.
     */
    @Test
    public void testNoUris() throws IOException {
        final String     jsonString = TestUtils.loadFixture("idonly.json");
        final JSONObject json       = JSONObject.fromObject(jsonString);
        final MockConfig config     = new MockConfig();

        MarathonBuilder builder = new MarathonBuilderImpl(config).setJson(json).build();
        assertNull("URIs should be null if none were in the JSON config", builder.getApp().getUris());
    }

    /**
     * Test that existing URIs are not deleted or overwritten on subsequence builds.
     */
    @Test
    public void testExistingUris() throws IOException {
        final String     jsonString = TestUtils.loadFixture("uris.json");
        final JSONObject json       = JSONObject.fromObject(jsonString);

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
        final String     jsonString = "{\"id\": \"testid\", \"uris\": \"http://example.com/artifact\"}";
        final JSONObject json       = JSONObject.fromObject(jsonString);
        final MockConfig config     = new MockConfig();

        try {
            new MarathonBuilderImpl(config).setJson(json).build();
            assertTrue("Should throw json parse exception", false);
        } catch (JsonSyntaxException jse) {
            assertTrue(true);
        }
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

        MockConfig() {
            uris = new ArrayList<>();
            labels = new ArrayList<>();
            env = new ArrayList<>();
        }

        @Override
        public String getAppId() {
            return appId;
        }

        @Override
        public String getUrl() {
            return url;
        }

        @Override
        public boolean getForceUpdate() {
            return forceUpdate;
        }

        @Override
        public String getDocker() {
            return docker;
        }

        @Override
        public boolean getDockerForcePull() {
            return dockerForcePull;
        }

        @Override
        public String getCredentialsId() {
            return credentialsId;
        }

        @Override
        public List<MarathonUri> getUris() {
            return uris;
        }

        @Override
        public List<MarathonLabel> getLabels() {
            return labels;
        }

        @Override
        public List<MarathonVars> getEnv() {
            return env;
        }
    }
}
