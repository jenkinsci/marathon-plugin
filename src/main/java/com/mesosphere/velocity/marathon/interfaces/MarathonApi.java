package com.mesosphere.velocity.marathon.interfaces;

import mesosphere.marathon.client.utils.MarathonException;

/**
 * Marathon API Interface
 *
 * @author luketornquist
 * @since 1/21/17
 */
public interface MarathonApi {
    String update(String appId, String jsonPayload, boolean forceUpdate) throws MarathonException;
}
