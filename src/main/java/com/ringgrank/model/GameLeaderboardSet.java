package com.ringgrank.model;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;

import com.ringgrank.service.GlobalLeaderboardManager;

/**
 * Manages all leaderboards (all-time and windowed) for a single game.
 * Must be Serializable for snapshot support.
 */
public class GameLeaderboardSet implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long gameId;
    private final Leaderboard allTimeLeaderboard = new Leaderboard();

    // Key: window identifier (e.g., "24h"), Value: Leaderboard for that window
    private final Map<String, Leaderboard> windowedLeaderboards = new ConcurrentHashMap<>();

    // Key: window identifier, Value: Duration of the window
    private final Map<String, Duration> windowDurations = new ConcurrentHashMap<>();

    private transient DelayQueue<GlobalLeaderboardManager.ExpiringScore> expiringScoresQueueRef;

    public GameLeaderboardSet(long gameId, DelayQueue<GlobalLeaderboardManager.ExpiringScore> expiringScoresQueueRef) {
        this.gameId = gameId;
        // Configure default windows
        configureWindow("24h", Duration.ofHours(24));
        this.expiringScoresQueueRef = expiringScoresQueueRef;
    }

    public void configureWindow(String windowKey, Duration duration) {
        windowedLeaderboards.putIfAbsent(windowKey, new Leaderboard());
        windowDurations.put(windowKey, duration);
    }

    public Leaderboard getLeaderboard(String windowKey) {
        if (windowKey == null || windowKey.trim().isEmpty()) {
            return allTimeLeaderboard;
        }
        return windowedLeaderboards.get(windowKey);
    }

    public void addScore(ScoreEntry entry) {
        // Always update all-time leaderboard
        allTimeLeaderboard.addOrUpdateScore(entry);

        // Update windowed leaderboards if the score is within the window
        Instant scoreTime = Instant.ofEpochMilli(entry.timestamp());
        windowedLeaderboards.forEach((windowKey, leaderboard) -> {
            Duration windowDuration = windowDurations.get(windowKey);
            if (windowDuration != null) {
                Instant windowStartTime = Instant.now().minus(windowDuration);
                if (scoreTime.isAfter(windowStartTime)) {
                    leaderboard.addOrUpdateScore(entry);
                    expiringScoresQueueRef.add(new GlobalLeaderboardManager.ExpiringScore(entry, gameId, windowKey,
                            scoreTime.plus(windowDuration).toEpochMilli()));
                }
            }
        });
    }

    public void setExpiringScoresQueueRef(DelayQueue<GlobalLeaderboardManager.ExpiringScore> expiringScoresQueueRef) {
        this.expiringScoresQueueRef = expiringScoresQueueRef;
    }

    public long getGameId() {
        return gameId;
    }

    public Map<String, Duration> getWindowDurations() {
        return windowDurations;
    }
}