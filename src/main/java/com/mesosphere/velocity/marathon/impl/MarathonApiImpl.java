package com.mesosphere.velocity.marathon.impl;

import com.mesosphere.velocity.marathon.interfaces.MarathonApi;
import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.model.v2.App;
import mesosphere.marathon.client.model.v2.Result;

import java.util.logging.Logger;

public class MarathonApiImpl implements MarathonApi {
    private static final Logger LOGGER = Logger.getLogger(MarathonApiImpl.class.getName());

    public MarathonApiImpl() {
    }

    /**
     * wait for n seconds
     *
     * @param seconds
     */
    private static void waitForSeconds(int seconds) {
        if (seconds > 0) {
            try {
                Thread.sleep(seconds * 1000L);
            } catch (InterruptedException e) {
                LOGGER.warning("A problem occured while waiting on a thread. The reason is: " + e.getMessage());
            }
        }
    }

    /**
     * Updating an app. If the timeout set by a value > 0, the method will wait until the deployment is done.
     *
     * @param client
     * @param app
     * @param forceUpdate
     * @param timeout
     * @return information, if the app could be deployed within a timaout range.
     */
    @Override
    public boolean update(Marathon client, App app, boolean forceUpdate, long timeout) {
        if (client == null || app == null) {
            throw new IllegalArgumentException("Necessary parameters are not set!");
        }

        Result result = client.updateApp(app.getId(), app, forceUpdate);
        boolean success = false;
        if (timeout > 0) {
            if (result != null && result.getDeploymentId() != null) {
                LOGGER.info(String.format("Create deployment with id: '%s'", result.getDeploymentId()));
                success = waitForDeploymentHasFinished(client, result.getDeploymentId(), timeout);
            } else {
                LOGGER.warning(String.format("Could not create deployment for app: '%s'", app != null ? app : "null"));
            }
        } else {
            success = true;
        }
        return success;
    }

    private boolean waitForDeploymentHasFinished(Marathon client, String deploymentId, long timeout) {
        LOGGER.info(String.format("Waiting for deployment with id='%s' has finished.", deploymentId != null ? deploymentId : "null"));
        boolean isActive = true;
        long startTimestamp = System.currentTimeMillis();
        while (isActive) {
            waitForSeconds(5); // 5 seconds
            isActive = client.getDeployments().parallelStream() //
                    .filter(deployment -> deployment.getId().equals(deploymentId)) //
                    .findFirst() //
                    .isPresent();
            if (timeout > 0 && (System.currentTimeMillis() - startTimestamp) >= timeout) {
                LOGGER.warning(String.format("The deployment with id='%s' has timed out after %dms.", deploymentId != null ? deploymentId : "null", timeout));
                break;
            }
        }
        return !isActive;
    }
}
