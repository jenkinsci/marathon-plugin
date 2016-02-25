package com.mesosphere.velocity.marathon.exceptions;

public class MarathonFileMissingException extends Exception {
    private final String filename;

    public MarathonFileMissingException(final String filename) {
        this.filename = filename;
    }

    @Override
    public String getMessage() {
        return "Could not find file '" + filename + "'";
    }

    @Override
    public String toString() {
        return "Could not find file '" + filename + "'";
    }
}
