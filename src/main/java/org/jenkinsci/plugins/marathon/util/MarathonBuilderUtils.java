package org.jenkinsci.plugins.marathon.util;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.MarathonClient;
import mesosphere.marathon.client.model.v2.App;
import mesosphere.marathon.client.utils.MarathonException;
import mesosphere.marathon.client.utils.ModelUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.marathon.interfaces.AppConfig;
import org.jenkinsci.plugins.marathon.fields.MarathonLabel;
import org.jenkinsci.plugins.marathon.fields.MarathonUri;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class MarathonBuilderUtils {
    public static final String MARATHON_JSON           = "marathon.json";
    public static final String MARATHON_RENDERED_JSON  = "marathon-rendered-${BUILD_NUMBER}.json";
    public static final String JSON_CONTAINER_FIELD    = "container";
    public static final String JSON_DOCKER_FIELD       = "docker";
    public static final String JSON_DOCKER_IMAGE_FIELD = "image";
    public static final String JSON_ID_FIELD           = "id";
    public static final String JSON_URI_FIELD          = "uris";
    public static final String JSON_EMPTY_CONTAINER    = "{\"type\": \"DOCKER\"}";

    /**
     * Set the docker "image" JSON value. This will create and set
     * empty JSON objects for container and docker if they do not
     * already exist within <code>marathonJson</code>.
     * <p>
     * This handles transforming Environment Variables to their
     * actual values.
     *
     * @param envVars      Jenkins environment variables
     * @param marathonJson Root JSON object
     */
    public static JSONObject setJsonDockerImage(final String image, final EnvVars envVars, final JSONObject marathonJson) {
        if (image != null && image.trim().length() > 0) {
            // get container -> docker -> image
            if (!marathonJson.has(MarathonBuilderUtils.JSON_CONTAINER_FIELD)) {
                marathonJson.element(MarathonBuilderUtils.JSON_CONTAINER_FIELD, JSONObject.fromObject(MarathonBuilderUtils.JSON_EMPTY_CONTAINER));
            }

            final JSONObject container = marathonJson.getJSONObject(MarathonBuilderUtils.JSON_CONTAINER_FIELD);
            if (!container.has(MarathonBuilderUtils.JSON_DOCKER_FIELD)) {
                container.element(MarathonBuilderUtils.JSON_DOCKER_FIELD, new JSONObject());
            }

            final JSONObject docker = container.getJSONObject(MarathonBuilderUtils.JSON_DOCKER_FIELD);
            docker.element(MarathonBuilderUtils.JSON_DOCKER_IMAGE_FIELD, Util.replaceMacro(image, envVars));
        }

        return marathonJson;
    }

    /**
     * Set the root "id" value. This handles transforming Environment
     * Variables to their actual values.
     *
     * @param envVars      Jenkins environment variables
     * @param marathonJson Root JSON object
     */
    public static JSONObject setJsonId(final String appid, final EnvVars envVars, final JSONObject marathonJson) {
        if (appid != null && appid.trim().length() > 0)
            marathonJson.put(MarathonBuilderUtils.JSON_ID_FIELD, Util.replaceMacro(appid, envVars));

        return marathonJson;
    }

    /**
     * Set the root "uris" JSON array with the URIs configured within
     * the Jenkins UI. This handles transforming Environment Variables
     * to their actual values.
     *
     * @param envVars Jenkins environment variables
     * @param json    Root JSON object
     */
    public static JSONObject setJsonUris(final List<MarathonUri> uris, final EnvVars envVars, final JSONObject json) {
        // TODO: Add checkbox to toggle removal vs merging
        json.element(MarathonBuilderUtils.JSON_URI_FIELD, new JSONArray());
        for (MarathonUri uri : uris) {
            json.accumulate(MarathonBuilderUtils.JSON_URI_FIELD, Util.replaceMacro(uri.getUri(), envVars));
        }

        return json;
    }

    public static JSONObject setJsonLabels(final List<MarathonLabel> labels, EnvVars envVars, JSONObject marathonJson) {
        if (!marathonJson.has("labels"))
            marathonJson.element("labels", new JSONObject());

        final JSONObject labelObject = marathonJson.getJSONObject("labels");
        for (MarathonLabel label : labels) {
            labelObject.element(Util.replaceMacro(label.getName(), envVars),
                    Util.replaceMacro(label.getValue(), envVars));
        }

        return marathonJson;
    }

    /**
     * Write json to file filename.
     *
     * @param filename File name for new file
     * @param json     JSON data
     * @throws IOException If filename is a directory or a file operation encounters
     *                     an issue
     */
    public static void writeJsonToFile(final FilePath filename, final App json) throws IOException, InterruptedException {
        if (filename.exists() && filename.isDirectory())
            throw new IOException("File '" + filename + "' is a directory; not overwriting.");

        filename.write(json.toString(), null);
    }

    public static App buildApp(final JSONObject json) {
        return ModelUtils.GSON.fromJson(json.toString(), App.class);
    }

    public static boolean validFile(final FilePath filename) throws IOException, InterruptedException {
        return filename.exists() && !filename.isDirectory();
    }

    public static FilePath getMarathonFile(final FilePath workspace) {
        return workspace.child(MARATHON_JSON);
    }

    public static JSONObject getJsonFromFile(final FilePath file) throws IOException, InterruptedException {
        final String content = file.readToString();
        return JSONObject.fromObject(content);
    }

    public static void writeRenderedFile(final String fileName, final App app, final FilePath ws, final Logger LOGGER) throws InterruptedException {
        if (fileName != null && fileName.trim().length() > 0) {
            final FilePath renderedFilepath = ws.child(fileName);
            try {
                MarathonBuilderUtils.writeJsonToFile(renderedFilepath, app);
                LOGGER.info("Wrote JSON to '" + renderedFilepath + "'");
            } catch (IOException e) {
                LOGGER.warning("Exception encountered when writing rendered JSON to '" + renderedFilepath + "'");
                LOGGER.warning(e.getLocalizedMessage());
            }
        } else {
            LOGGER.warning("Failed to create rendered JSON file.");
        }
    }

    public static void doPerform(final FilePath workspace,
                                 final EnvVars envVars,
                                 final AppConfig config,
                                 final Logger LOGGER) throws IOException, InterruptedException, MarathonException {
        final FilePath marathonFile = getMarathonFile(workspace);

        if (validFile(marathonFile)) {
            final JSONObject marathonJson = getJsonFromFile(marathonFile);

            if (marathonJson != null && !marathonJson.isEmpty() && !marathonJson.isArray()) {
                final String marathonUrl = Util.replaceMacro(config.getUrl(), envVars);

                setJsonId(config.getAppId(), envVars, marathonJson);
                setJsonDockerImage(config.getDocker(), envVars, marathonJson);
                setJsonUris(config.getUris(), envVars, marathonJson);
                setJsonLabels(config.getLabels(), envVars, marathonJson);

                /*
                 * JSON is done being constructed; done merging marathon.json
                 * with Jenkins configuration and environment variables.
                 */
                final App    app      = buildApp(marathonJson);
                final String fileName = Util.replaceMacro(MarathonBuilderUtils.MARATHON_RENDERED_JSON, envVars);
                writeRenderedFile(fileName, app, workspace, LOGGER);

                // hit Marathon here
                final Marathon marathon = MarathonClient.getInstance(marathonUrl);
                marathon.updateApp(app.getId(), app, false);   // uses PUT
            }
        }
    }
}
