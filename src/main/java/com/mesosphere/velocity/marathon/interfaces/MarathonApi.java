package com.mesosphere.velocity.marathon.interfaces;

import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.model.v2.App;

public interface MarathonApi {

    /**
     * API for deploying
     *
     * @param client
     * @param app
     * @param forceUpdate
     * @param timeout
     * @return the result, whether a deployment
     */
    boolean update(Marathon client, App app, boolean forceUpdate, long timeout);
}
