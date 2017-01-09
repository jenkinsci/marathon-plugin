package com.mesosphere.velocity.marathon.impl;

import com.mesosphere.velocity.marathon.DeploymentConfig;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileInvalidException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileMissingException;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import hudson.FilePath;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({FilePath.class})
public class MarathonBuilderImplTest {
    @Rule
    public final ExpectedException exception = ExpectedException.none();
    @Mock
    private DeploymentConfig appConfig;
    private MarathonBuilder builder;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSetJson() {
        final String     key  = "key";
        final String     val  = "testvalue";
        final JSONObject json = new JSONObject();

        builder = new MarathonBuilderImpl(null, null, appConfig);
        assertNull("JSON is null on initial creation.", builder.getJson());

        builder.setJson(json);
        assertNotNull("An empty JSON object is set correctly.", builder.getJson());

        json.put(key, val);
        builder.setJson(json);
        assertEquals("Simple JSON object with values is set correctly.", builder.getJson().getString(key), val);
    }

    @Test
    public void testReadNonexistingFile()
            throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException {
        final String   filename = "somefile";
        final FilePath wsMock   = PowerMockito.mock(FilePath.class);
        final FilePath fileMock = PowerMockito.mock(FilePath.class);
        when(wsMock.child(anyString())).thenReturn(fileMock);
        when(fileMock.exists()).thenReturn(false);

        // create builder
        builder = new MarathonBuilderImpl(null, null, appConfig);

        // setup FileMissing exception
        exception.expect(MarathonFileMissingException.class);
        builder.setWorkspace(wsMock).read(filename);
    }

    @Test
    public void testReadDirectoryFile() throws InterruptedException, MarathonFileMissingException, MarathonFileInvalidException, IOException {
        final String   filename = "somefile";
        final FilePath wsMock   = PowerMockito.mock(FilePath.class);
        final FilePath fileMock = PowerMockito.mock(FilePath.class);
        when(wsMock.child(anyString())).thenReturn(fileMock);
        when(fileMock.exists()).thenReturn(true);
        when(fileMock.isDirectory()).thenReturn(true);

        // create builder
        builder = new MarathonBuilderImpl(null, null, appConfig);

        // setup FileInvalid exception
        exception.expect(MarathonFileInvalidException.class);
        builder.setWorkspace(wsMock).read(filename);
    }

    @Test
    public void testReadPositive() throws Exception {
        final String     filename     = "somefile";
        final FilePath   wsMock       = PowerMockito.mock(FilePath.class);
        final FilePath   fileMock     = PowerMockito.mock(FilePath.class);
        final JSONObject expectedJson = new JSONObject();

        when(wsMock.child(anyString())).thenReturn(fileMock);
        when(fileMock.exists()).thenReturn(true);
        when(fileMock.isDirectory()).thenReturn(false);

        // the magic...
        when(fileMock.readToString()).thenReturn("{}");

        builder = new MarathonBuilderImpl(null, null, appConfig);
        builder.setWorkspace(wsMock).read(filename);
        assertEquals("Empty JSON Object was read in", expectedJson, builder.getJson());

        // now non-empty
        when(fileMock.readToString()).thenReturn("{\"id\": \"myid\"}");
        expectedJson.put("id", "myid");
        builder.read(filename);
        assertEquals("JSON should have same id",
                expectedJson.getString("id"), builder.getJson().getString("id"));
    }

    @Test
    public void testNoUris() {
        final String     jsonString = "{\"id\": \"testid\"}";
        final JSONObject json       = JSONObject.fromObject(jsonString);

        builder = new MarathonBuilderImpl(null, null, appConfig).setJson(json).build();
        assertNull(builder.getJson().get("uris"));

        when(appConfig.getUris()).thenReturn(Collections.singletonList(
                new MarathonUri("http://example.com/artifact")));
        builder = builder.build();
        final JSONArray uris = builder.getJson().getJSONArray("uris");
        assertEquals(1, uris.size());
        assertEquals("http://example.com/artifact", uris.get(0));
    }

    @Test
    public void testExistingUris() {
        final String     jsonString = "{\"id\": \"testid\", \"uris\": [\"http://example.com/artifact\"]}";
        final JSONObject json       = JSONObject.fromObject(jsonString);

        builder = new MarathonBuilderImpl(null, null, appConfig).setJson(json).build();
        assertEquals(1, builder.getJson().getJSONArray("uris").size());

        final String item = builder.getJson().getJSONArray("uris").getString(0);
        assertEquals("http://example.com/artifact", item);

        when(appConfig.getUris()).thenReturn(Collections.singletonList(new MarathonUri("http://example.com/valid_artifact")));
        builder = builder.build();
        assertEquals(2, builder.getJson().getJSONArray("uris").size());
    }

    @Test
    public void testInvalidTypeUris() {
        final String     jsonString = "{\"id\": \"testid\", \"uris\": \"http://example.com/artifact\"}";
        final JSONObject json       = JSONObject.fromObject(jsonString);

        when(appConfig.getUris()).thenReturn(Collections.singletonList(new MarathonUri("http://example.com/valid_artifact")));

        builder = new MarathonBuilderImpl(null, null, appConfig).setJson(json).build();
        final JSONArray uris = builder.getJson().getJSONArray("uris");
        assertEquals(1, uris.size());
        final String item = uris.getString(0);
        assertEquals("http://example.com/valid_artifact", item);
    }

}