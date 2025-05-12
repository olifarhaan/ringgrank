package com.ringgrank.exception;

/**
 * Exception thrown when a user is not found in a specific leaderboard.
 */
public class UserNotFoundInLeaderboardException extends RuntimeException {
    public UserNotFoundInLeaderboardException(String message) {
        super(message);
    }

    public UserNotFoundInLeaderboardException(String message, Throwable cause) {
        super(message, cause);
    }
} 