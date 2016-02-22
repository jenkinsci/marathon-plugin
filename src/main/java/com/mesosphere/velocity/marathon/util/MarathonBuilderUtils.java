package com.mesosphere.velocity.marathon.util;

public class MarathonBuilderUtils {
    public static final String MARATHON_JSON           = "marathon.json";
    public static final String MARATHON_RENDERED_JSON  = "marathon-rendered-${BUILD_NUMBER}.json";
    public static final String JSON_CONTAINER_FIELD    = "container";
    public static final String JSON_DOCKER_FIELD       = "docker";
    public static final String JSON_DOCKER_IMAGE_FIELD = "image";
    public static final String JSON_ID_FIELD           = "id";
    public static final String JSON_URI_FIELD          = "uris";
    public static final String JSON_EMPTY_CONTAINER    = "{\"type\": \"DOCKER\"}";
}
