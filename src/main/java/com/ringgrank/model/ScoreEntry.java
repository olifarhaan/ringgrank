package com.ringgrank.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a single score entry in the leaderboard.
 * Uses numeric IDs for userId and gameId.
 * Implements Comparable for sorting (highest score first, then earliest
 * timestamp).
 */
public record ScoreEntry(
        long userId,
        long gameId,
        long score,
        long timestamp) implements Comparable<ScoreEntry>, Serializable {

    /**
     * Compares this ScoreEntry with another for ordering.
     * Primary sort: by score in descending order (higher score is better).
     * Secondary sort: by timestamp in ascending order (earlier submission is better
     * for ties).
     *
     * @param other The other ScoreEntry to compare against.
     * @return a negative integer, zero, or a positive integer as this object
     *         is less than, equal to, or greater than the specified object.
     */
    @Override
    public int compareTo(ScoreEntry other) {
        // Compare scores (descending)
        int scoreCompare = Long.compare(other.score, this.score);
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        // If scores are tied, compare timestamps (ascending - earlier is better)
        return Long.compare(this.timestamp, other.timestamp);
    }

    // Override equals and hashCode to ensure correct behavior in Sets/Maps if
    // needed,
    // especially if we were to update entries. Records provide this by default
    // based on all fields.
    // However, for leaderboard logic, a user typically has one "best" score entry,
    // so identity based on userId within a specific leaderboard context is often
    // key.
    // The default record implementation is fine here.

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ScoreEntry that = (ScoreEntry) o;
        // For leaderboard purposes, often a score entry is unique by userId within a
        // game.
        // However, if a user can have multiple distinct scores recorded (e.g. not just
        // their best),
        // then all fields matter. For this implementation, we assume a user's single
        // best score
        // is what's tracked primarily by their userId, but the ScoreEntry itself is
        // defined by all its components.
        // The compareTo method handles ranking.
        return score == that.score &&
                timestamp == that.timestamp &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(gameId, that.gameId); // gameId might be redundant if entries are game-scoped
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, gameId, score, timestamp);
    }
}