package com.ringgrank.exception;

/**
 * Exception thrown when a provided window string is malformed or unsupported.
 */
public class InvalidWindowException extends RuntimeException {
    public InvalidWindowException(String message) {
        super(message);
    }

    public InvalidWindowException(String message, Throwable cause) {
        super(message, cause);
    }
} 