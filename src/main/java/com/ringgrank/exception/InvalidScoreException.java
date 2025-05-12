package com.ringgrank.exception;

/**
 * Exception thrown for invalid score data that isn't caught by basic DTO validation.
 * For example, a timestamp that is in the future.
 */
public class InvalidScoreException extends RuntimeException {
    public InvalidScoreException(String message) {
        super(message);
    }

    public InvalidScoreException(String message, Throwable cause) {
        super(message, cause);
    }
} 