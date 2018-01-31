package com.mesosphere.velocity.marathon.interfaces;

import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.model.v2.App;

import java.util.concurrent.TimeoutException;

public interface MarathonApi {

    /**
     * API for deploying
     *
     * @param client
     * @param app
     * @param forceUpdate
     * @param timeout
     * @throws TimeoutException the deployment timed out after the given timeout value
     */
    void update(Marathon client, App app, boolean forceUpdate, long timeout) throws TimeoutException;
}
