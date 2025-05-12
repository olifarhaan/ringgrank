package com.ringgrank.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Manages a single leaderboard instance (either all-time or a specific window).
 * Contains the sorted set of scores and a map for quick user score lookup.
 * This class is thread-safe and serializable for snapshot support.
 */
public class Leaderboard implements Serializable {
    private static final long serialVersionUID = 1L;

    // Stores all score entries, sorted by score (desc) and then timestamp (asc)
    private final NavigableSet<ScoreEntry> sortedScores;

    // Maps userId to their current ScoreEntry for O(1) lookup
    private final Map<Long, ScoreEntry> userScores;

    public Leaderboard() {
        this.sortedScores = new ConcurrentSkipListSet<>();
        this.userScores = new ConcurrentHashMap<>();
    }

    public void addOrUpdateScore(ScoreEntry newEntry) {
        ScoreEntry oldEntry = userScores.get(newEntry.userId());

        if (oldEntry != null) {
            sortedScores.remove(oldEntry);
        }

        sortedScores.add(newEntry);
        userScores.put(newEntry.userId(), newEntry);
    }

    public void removeScore(ScoreEntry entryToRemove) {
        if (entryToRemove == null) {
            return;
        }

        sortedScores.remove(entryToRemove);
        userScores.computeIfPresent(entryToRemove.userId(),
                (userId, currentEntry) -> currentEntry.equals(entryToRemove) ? null : currentEntry);
    }

    public ScoreEntry getUserScore(Long userId) {
        return userScores.get(userId);
    }

    public List<ScoreEntry> getTopK(int k) {
        if (k <= 0) {
            return Collections.emptyList();
        }

        List<ScoreEntry> topK = new ArrayList<>(k);
        int count = 0;
        for (ScoreEntry entry : sortedScores) {
            if (count >= k) {
                break;
            }
            topK.add(entry);
            count++;
        }
        return topK;
    }

    public int getUserRank(Long userId) {
        ScoreEntry userEntry = userScores.get(userId);
        if (userEntry == null) {
            return -1; // User not found
        }

        int rank = 1;
        for (ScoreEntry entry : sortedScores) {
            if (entry.equals(userEntry)) {
                return rank;
            }
            rank++;
        }
        return -1; // Should not happen if userEntry was found in userBestScores
    }

    public int getTotalPlayers() {
        return sortedScores.size();
    }

    public void clear() {
        sortedScores.clear();
        userScores.clear();
    }
}