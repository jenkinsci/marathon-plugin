package org.jenkinsci.plugins.marathon.exceptions;

public class MarathonFileInvalidException extends Exception {
    private String message;

    public MarathonFileInvalidException(final String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return message;
    }
}
