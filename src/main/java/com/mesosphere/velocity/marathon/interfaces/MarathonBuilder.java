package com.mesosphere.velocity.marathon.interfaces;

import com.mesosphere.velocity.marathon.exceptions.AuthenticationException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileInvalidException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileMissingException;
import com.mesosphere.velocity.marathon.impl.MarathonBuilderImpl;
import hudson.EnvVars;
import hudson.FilePath;
import mesosphere.marathon.client.utils.MarathonException;
import net.sf.json.JSONObject;

import java.io.IOException;

/**
 * This builds {@see MarathonClient}s from Jenkins, file system, and JSON pieces.
 * This allows the construction of the final payload as well as sending it to the target Marathon instance.
 */
public abstract class MarathonBuilder {
    /**
     * Local URL value that may be different than what was passed through config.
     */
    private String url;

    /**
     * Create a new builder instance from config.
     *
     * @param config Application configuration
     * @return A new builder
     */
    public static MarathonBuilder getBuilder(final AppConfig config) {
        return new MarathonBuilderImpl(config);
    }

    public String getURL() {
        return this.url;
    }

    public void setURL(final String url) {
        this.url = url;
    }

    /**
     * Update the Marathon application.
     *
     * @return This builder
     * @throws MarathonException
     */
    public abstract MarathonBuilder update() throws MarathonException, AuthenticationException;

    /**
     * Read in filename as JSON.
     *
     * @param filename Path to the JSON file
     * @return This builder
     * @throws IOException
     * @throws InterruptedException
     * @throws MarathonFileMissingException
     * @throws MarathonFileInvalidException
     */
    public abstract MarathonBuilder read(final String filename)
            throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException;

    /**
     * Read in default file (marathon.json) as JSON.
     *
     * @return This builder
     * @throws IOException
     * @throws InterruptedException
     * @throws MarathonFileMissingException
     * @throws MarathonFileInvalidException
     * @see #read(String)
     */
    public abstract MarathonBuilder read()
            throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException;

    public abstract JSONObject getJson();

    /**
     * Set the JSON for this builder to json. This will overwrite the value set by {@link #read(String)}.
     *
     * @param json The JSON value to use for this builder
     * @return This builder
     */
    public abstract MarathonBuilder setJson(final JSONObject json);

    /**
     * Set the Jenkins Environment Variables to vars. These are used when building the final Marathon
     * payload to convert Jenkins variables to their values.
     *
     * @param vars Jenkins environment variables
     * @return This builder
     */
    public abstract MarathonBuilder setEnvVars(final EnvVars vars);

    /**
     * Set the Application Configuration to config.
     *
     * @param config application configuration
     * @return This builder
     */
    public abstract MarathonBuilder setConfig(final AppConfig config);

    /**
     * Set the Jenkins workspace to ws.
     *
     * @param ws Jenkins workspace
     * @return This builder
     */
    public abstract MarathonBuilder setWorkspace(final FilePath ws);

    /**
     * Compose the builder. This populates certain fields with the proper Jenkins values and creates a
     * Marathon Client.
     *
     * @return This builder
     * @see mesosphere.marathon.client.MarathonClient
     */
    public abstract MarathonBuilder build();

    /**
     * Write the JSON that will be sent to the target Marathon instance to filename.
     *
     * @param filename File to write rendered JSON
     * @return This builder
     * @throws InterruptedException
     * @throws MarathonFileInvalidException
     * @throws IOException
     */
    public abstract MarathonBuilder toFile(final String filename)
            throws InterruptedException, MarathonFileInvalidException, IOException;

    /**
     * Write the JSON that will be sent to the target Marathon instance to the default file
     * (marathon-rendered-${BUILD_NUMBER}.json).
     *
     * @return This builder
     * @throws InterruptedException
     * @throws IOException
     * @throws MarathonFileInvalidException
     * @see #toFile(String)
     */
    public abstract MarathonBuilder toFile()
            throws InterruptedException, IOException, MarathonFileInvalidException;
}
