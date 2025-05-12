package com.ringgrank.exception;

/**
 * Exception thrown when an operation is attempted on a game that is not found.
 */
public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(String message) {
        super(message);
    }

    public GameNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
} 