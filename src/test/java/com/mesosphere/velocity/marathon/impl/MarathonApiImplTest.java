package com.mesosphere.velocity.marathon.impl;

import com.mesosphere.velocity.marathon.interfaces.MarathonApi;
import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.model.v2.App;
import mesosphere.marathon.client.model.v2.Deployment;
import mesosphere.marathon.client.model.v2.Result;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MarathonApiImplTest {

    private static final boolean DEFAULT_TEST_FORCEUPDATE = true;
    private static final long DEFAULT_TEST_TIMEOUT = 2000L;
    private static final String TEST_APP_ID = "/test/appId";
    private static final String TEST_DEPLOYMENT_ID = "1234-5678";

    private MarathonApi marathonApi;
    private Marathon client;
    private App app;

    @Before
    public void setUp() throws Exception {
        this.marathonApi = new MarathonApiImpl();
        this.app = mock(App.class);
        when(this.app.getId()).thenReturn(TEST_APP_ID);
        this.client = mock(Marathon.class);
    }

    @Test
    public void testUpdate_deploymentSuccessfull() throws Exception {
        // given
        boolean forceUpdate = DEFAULT_TEST_FORCEUPDATE;
        long timeout = DEFAULT_TEST_TIMEOUT;
        Result result = mock(Result.class);
        when(result.getDeploymentId()).thenReturn(TEST_DEPLOYMENT_ID);
        when(this.client.updateApp(this.app.getId(), this.app, forceUpdate)).thenReturn(result);
        // when
        boolean success = this.marathonApi.update(this.client, this.app, forceUpdate, timeout);
        // then
        assertTrue(success);
    }

    @Test
    public void testUpdate_deploymentTimedout() throws Exception {
        // given
        boolean forceUpdate = DEFAULT_TEST_FORCEUPDATE;
        long timeout = DEFAULT_TEST_TIMEOUT;
        Result result = mock(Result.class);
        when(result.getDeploymentId()).thenReturn(TEST_DEPLOYMENT_ID);
        when(this.client.updateApp(this.app.getId(), this.app, forceUpdate)).thenReturn(result);

        Deployment deployment = mock(Deployment.class);
        when(deployment.getId()).thenReturn(TEST_DEPLOYMENT_ID);
        when(this.client.getDeployments()).thenReturn(Arrays.asList(deployment));
        // when
        boolean success = this.marathonApi.update(this.client, this.app, forceUpdate, timeout);
        // then
        assertFalse(success);
    }

    @Test(expected = IllegalArgumentException.class) // then
    public void testUpdate_clientNotSet() throws Exception {
        // given
        boolean forceUpdate = DEFAULT_TEST_FORCEUPDATE;
        long timeout = DEFAULT_TEST_TIMEOUT;
        // when
        this.marathonApi.update(null, this.app, forceUpdate, timeout);
    }

    @Test(expected = IllegalArgumentException.class) // then
    public void testUpdate_appNotSet() throws Exception {
        // given
        boolean forceUpdate = DEFAULT_TEST_FORCEUPDATE;
        long timeout = DEFAULT_TEST_TIMEOUT;
        // when
        this.marathonApi.update(this.client, null, forceUpdate, timeout);
    }
}