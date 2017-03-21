package com.mesosphere.velocity.marathon;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class TestUtils {

    public static String getHttpAddresss(final MockWebServer httpServer) {
        return httpServer.url("/").toString();
    }

    /**
     * Enqueue a JSON response with the proper headers set.
     *
     * @param responseStr JSON payload
     */
    public static void enqueueJsonResponse(final MockWebServer httpServer, final String responseStr) {
        httpServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(responseStr));
    }

    /**
     * Enqueue an empty response with given status code.
     *
     * @param statusCode status code to return
     */
    public static void enqueueFailureResponse(final MockWebServer httpServer, final int statusCode) {
        httpServer.enqueue(new MockResponse().setResponseCode(statusCode));
    }

}
