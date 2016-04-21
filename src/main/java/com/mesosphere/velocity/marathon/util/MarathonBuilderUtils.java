package com.mesosphere.velocity.marathon.util;

public class MarathonBuilderUtils {
    /**
     * Default Marathon Application Definition file.
     */
    public static final String MARATHON_JSON           = "marathon.json";
    /**
     * Default filepath for the rendered JSON that was sent to the Marathon instance.
     */
    public static final String MARATHON_RENDERED_JSON  = "marathon-rendered-${BUILD_NUMBER}.json";
    /*
     * The following are all JSON field names that correspond to the Marathon Application definition.
     */
    /**
     * The field off the root App JSON that holds container (docker, etc.) information.
     */
    public static final String JSON_CONTAINER_FIELD    = "container";
    /**
     * The field contained within the "container" JSON object that holds the docker information.
     */
    public static final String JSON_DOCKER_FIELD       = "docker";
    /**
     * The docker image field. This is found within the "docker" JSON object.
     */
    public static final String JSON_DOCKER_IMAGE_FIELD = "image";
    /**
     * Application Id field; available from the root of the App JSON.
     */
    public static final String JSON_ID_FIELD           = "id";
    /**
     * URIs field; available from the root of the App JSON.
     */
    public static final String JSON_URI_FIELD          = "uris";
    /**
     * Empty container JSON object. This is used when the docker image is configured within
     * the Jenkins UI, but the "container" field is not present within the base Application
     * Definition.
     */
    public static final String JSON_EMPTY_CONTAINER    = "{\"type\": \"DOCKER\"}";

    /**
     * Remove the trailing slash from url.
     *
     * @param url the URL
     * @return URL with the trailing slash removed, if it exists.
     */
    public static String rmSlashFromUrl(final String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
