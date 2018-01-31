package com.mesosphere.velocity.marathon.interfaces;

import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.fields.MarathonVars;

import java.util.List;

public interface AppConfig {
    /**
     * Get the configured Marathon Application Id.
     *
     * @return the Marathon Application Id
     */
    String getAppId();

    /**
     * Get the URL for the target Marathon instance.
     *
     * @return Marathon instance URL
     */
    String getUrl();

    /**
     * Get the value of whether the Marathon update should be forced. If set to
     * True, an in-progress deployment of this application will be canceled in
     * favor of this update. If set to False, an error will occur because the
     * application has a deployment in progress.
     *
     * @return whether to force the Marathon application update
     */
    boolean getForceUpdate();

    /**
     * Get the configured docker image name.
     *
     * @return Docker image name
     */
    String getDocker();

    /**
     * Get the configured docker image force pull flag.
     *
     * @return Docker image force pull flag
     */
    boolean getDockerForcePull();

    /**
     * Get the Jenkins credentials id for this configuration.
     *
     * @return credentials id
     */
    String getCredentialsId();

    /**
     * Return a list of configured URIs. These will be downloaded into the container
     * sandbox prior to the Marathon application starting.
     *
     * @return list of URIs
     */
    List<MarathonUri> getUris();

    /**
     * Return a list of labels. Marathon uses Labels to attach metadata to an application.
     *
     * @return list of labels
     */
    List<MarathonLabel> getLabels();

    /**
     * Return a list of environment variables.
     *
     * @return list of environment variables
     */
    List<MarathonVars> getEnv();

    /**
     * Return the value after a deployment times out
     *
     * @return timeout value in milliseconds
     */
    long getTimeout();
}
