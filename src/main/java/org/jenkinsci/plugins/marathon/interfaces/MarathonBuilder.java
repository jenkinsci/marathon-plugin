package org.jenkinsci.plugins.marathon.interfaces;

import hudson.EnvVars;
import hudson.FilePath;
import mesosphere.marathon.client.utils.MarathonException;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.marathon.exceptions.MarathonFileInvalidException;
import org.jenkinsci.plugins.marathon.exceptions.MarathonFileMissingException;
import org.jenkinsci.plugins.marathon.impl.MarathonBuilderImpl;

import java.io.IOException;

public abstract class MarathonBuilder {
    public static MarathonBuilder getBuilder(final AppConfig config) {
        return new MarathonBuilderImpl(config);
    }

    public abstract MarathonBuilder create() throws MarathonException;

    /**
     * Update the Marathon deployment.
     *
     * @throws MarathonException
     */
    public abstract MarathonBuilder update() throws MarathonException;

    public abstract MarathonBuilder delete() throws MarathonException;

    public abstract MarathonBuilder read(final String filename) throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException;

    public abstract MarathonBuilder read() throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException;

    public abstract MarathonBuilder setJson(final JSONObject json);

    public abstract MarathonBuilder setEnvVars(final EnvVars vars);

    public abstract MarathonBuilder setConfig(final AppConfig config);

    public abstract MarathonBuilder setWorkspace(final FilePath ws);

    public abstract MarathonBuilder build();

    public abstract MarathonBuilder toFile(final String filename) throws InterruptedException, MarathonFileInvalidException, IOException;

    public abstract MarathonBuilder toFile() throws InterruptedException, IOException, MarathonFileInvalidException;
}
