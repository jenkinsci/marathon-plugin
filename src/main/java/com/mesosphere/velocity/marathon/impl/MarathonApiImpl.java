package com.mesosphere.velocity.marathon.impl;

import com.mesosphere.velocity.marathon.interfaces.MarathonApi;
import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.model.v2.App;
import mesosphere.marathon.client.model.v2.Result;

import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class MarathonApiImpl implements MarathonApi {
    private static final Logger LOGGER = Logger.getLogger(MarathonApiImpl.class.getName());
    private static final int CHECK_DEPLOYMENT_WAIT_FREQUENCY_SECS = 5;

    public MarathonApiImpl() {
    }

    /**
     * Updating an app. If the timeout set by a value greater then 0, the method will wait until the deployment is done.
     *
     * @param client Marathon client
     * @param app Marathon app model
     * @param forceUpdate force updating a deployment of a service
     * @param timeout number of milliseconds after the deployment should time out
     * @throws TimeoutException the deployment timed out after the given timeout value
     */
    @Override
    public void update(Marathon client, App app, boolean forceUpdate, long timeout) throws TimeoutException {
        if (client == null || app == null) {
            throw new IllegalArgumentException("Necessary parameters are not set!");
        }

        Result result = client.updateApp(app.getId(), app, forceUpdate);
        if (timeout > 0) {
            if (result != null && result.getDeploymentId() != null) {
                LOGGER.info(String.format("Create deployment with id: '%s'", result.getDeploymentId()));
                waitForDeploymentHasFinished(client, result.getDeploymentId(), timeout);
            } else {
                LOGGER.warning(String.format("Could not create deployment for app: '%s'", app != null ? app : "null"));
            }
        }
    }

    private static void waitForDeploymentHasFinished(Marathon client, String deploymentId, long timeout) throws TimeoutException {
        LOGGER.info(String.format("Waiting for deployment with id='%s' has finished.", deploymentId != null ? deploymentId : "null"));
        final long startTimestamp = System.currentTimeMillis();
        while (true) {
            final boolean isDeploymentActive = client.getDeployments().parallelStream() //
                    .filter(deployment -> deployment.getId().equals(deploymentId)) //
                    .findFirst() //
                    .isPresent();
            if(!isDeploymentActive){
                break;
            }else if(timeout > 0 && (System.currentTimeMillis() - startTimestamp) >= timeout) {
                final String timeoutMessage = String.format("The deployment with id='%s' has timed out after %dms.", deploymentId != null ? deploymentId : "null", timeout);
                LOGGER.warning(timeoutMessage);
                throw new TimeoutException(timeoutMessage);
            }
            try {
                Thread.sleep(CHECK_DEPLOYMENT_WAIT_FREQUENCY_SECS * 1000L);
            } catch (InterruptedException e) {
                LOGGER.warning("A problem occured while waiting on a thread. The reason is: " + e.getMessage());
                break;
            }
        }
    }
}
