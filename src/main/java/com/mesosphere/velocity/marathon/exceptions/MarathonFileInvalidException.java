package com.mesosphere.velocity.marathon.exceptions;

public class MarathonFileInvalidException extends Exception {
    private final String message;

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
