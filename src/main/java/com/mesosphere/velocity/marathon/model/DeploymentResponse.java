package com.mesosphere.velocity.marathon.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

/**
 * Deployment Response Payload from Marathon
 *
 * @author ltornquist
 * @version 4/14/17
 */
public class DeploymentResponse {

    private static final Logger LOG = LoggerFactory.getLogger(DeploymentResponse.class);
    private static final Date UNKNOWN_VERSION = new Date();

    private Date version;
    private String deploymentId;

    public Date getVersion() {
        return version;
    }

    public void setVersion(Date version) {
        this.version = version;
    }

    public void setVersion(String version) {
        try {
            this.version = DateFormat.getDateTimeInstance().parse(version);
        }
        catch (ParseException e) {
            LOG.error("Error occurred while parsing version: " + version, e);
            this.version = UNKNOWN_VERSION;
        }
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }
}
