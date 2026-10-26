package com.urlshortener.exception;

public class AliasTakenException extends RuntimeException {
    public AliasTakenException(String alias) {
        super("Custom code is already taken: " + alias);
    }
}
