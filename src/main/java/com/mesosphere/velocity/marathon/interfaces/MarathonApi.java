package com.mesosphere.velocity.marathon.interfaces;

import com.google.common.base.Optional;
import com.mesosphere.velocity.marathon.model.DeploymentResponse;
import mesosphere.marathon.client.utils.MarathonException;

/**
 * Marathon API Interface
 *
 * @author luketornquist
 * @since 1/21/17
 */
public interface MarathonApi {
    Optional<DeploymentResponse> update(String appId, String jsonPayload, boolean forceUpdate) throws MarathonException;
}
