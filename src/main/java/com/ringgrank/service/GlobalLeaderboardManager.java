package com.ringgrank.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ringgrank.model.GameLeaderboardSet;
import com.ringgrank.model.Leaderboard;
import com.ringgrank.model.ScoreEntry;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Manages leaderboard data for all games.
 * Handles persistence (WAL, Snapshots) and recovery.
 */
@Component
public class GlobalLeaderboardManager {
    private final Logger logger = LoggerFactory.getLogger(GlobalLeaderboardManager.class);
    private final ConcurrentHashMap<Long, GameLeaderboardSet> gameLeaderboards = new ConcurrentHashMap<>();
    private final Map<Long, Lock> gameCreationLocks = new ConcurrentHashMap<>();

    @Value("${leaderboard.wal.path:./data/wal/scores}")
    private String walFilePathString;
    private Path walFilePath;
    private Path archivedWalFilePath;

    @Value("${leaderboard.snapshot.path:./data/snapshot/leaderboard}")
    private String snapshotFilePathString;
    private Path snapshotFilePath;
    private Path tempSnapshotFilePath;

    @Value("${leaderboard.snapshot.interval:3600000}") // Default: 1 hour
    private long snapshotInterval;

    private final DelayQueue<ExpiringScore> expiringScores = new DelayQueue<>();
    private volatile boolean isRunning = true;
    private Thread expirationProcessorThread;

    @PostConstruct
    public void initialize() {
        logger.info("Initializing GlobalLeaderboardManager");
        this.walFilePath = Paths.get(walFilePathString);
        this.archivedWalFilePath = Paths.get(walFilePathString + ".archive"); // Define archived WAL path
        this.snapshotFilePath = Paths.get(snapshotFilePathString);
        this.tempSnapshotFilePath = Paths.get(snapshotFilePathString + ".tmp");
        // Create necessary directories
        try {
            Files.createDirectories(walFilePath.getParent());
            Files.createDirectories(snapshotFilePath.getParent());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create data directories", e);
        }

        // Load data from snapshot and WAL
        long lastTimestamp = loadFromSnapshot(snapshotFilePath);
        replayWALFile(walFilePath, lastTimestamp);

        expirationProcessorThread = new Thread(this::processExpiringScores, "ScoreExpirationProcessor");
        expirationProcessorThread.setDaemon(true);
        expirationProcessorThread.start();
    }

    @PreDestroy
    public void shutdown() {
        isRunning = false;
        if (expirationProcessorThread != null) {
            expirationProcessorThread.interrupt(); // Interrupt the expiration processor thread
            try {
                expirationProcessorThread.join(5000); // Wait for it to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        createSnapshot();
    }

    public void recordScore(ScoreEntry scoreEntry) {
        // 1. Write to WAL first for durability
        writeToWAL(scoreEntry);

        // 2. Update in-memory structures
        GameLeaderboardSet gameSet = gameLeaderboards.get(scoreEntry.gameId());
        if (gameSet == null) {
            Lock lock = gameCreationLocks.computeIfAbsent(
                    scoreEntry.gameId(),
                    k -> new ReentrantLock());
            lock.lock();
            try {
                gameSet = gameLeaderboards.computeIfAbsent(scoreEntry.gameId(),
                        id -> new GameLeaderboardSet(id, expiringScores));
                gameSet.addScore(scoreEntry);
            } finally {
                lock.unlock();
            }
        }
        gameSet.addScore(scoreEntry);
    }

    private void writeToWAL(ScoreEntry entry) {
        String entryString = String.format("%d,%d,%d,%d%n",
                entry.timestamp(),
                entry.gameId(),
                entry.userId(),
                entry.score());
        try (FileOutputStream fos = new FileOutputStream(this.walFilePath.toFile(), true);
                FileChannel channel = fos.getChannel();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {

            writer.write(entryString);
            writer.flush();
            /*
             * A trade-off has to be made between durability and performance.
             * 
             * channel.force(true) will force the OS to flush the file to disk,
             * but it is slow. But still I have chosen not to use it because the performance
             * gain is significant.
             * 
             * writer.flush() is faster, but the OS may not flush the file to disk
             * immediately.
             * 
             * I have chosen to use writer.flush() because the performance gain is
             * significant.
             * 
             * If you want to use channel.force(true), you can uncomment the line.
             * 
             */
            // channel.force(true);

        } catch (IOException e) {
            throw new RuntimeException("Failed to write to WAL", e);
        }
    }

    @Scheduled(fixedDelayString = "${leaderboard.snapshot.interval:3600000}")
    public void createSnapshot() {
        logger.info("Creating snapshot");
        try (ObjectOutputStream oos = new ObjectOutputStream(
                Files.newOutputStream(tempSnapshotFilePath, StandardOpenOption.CREATE))) {
            for (Map.Entry<Long, GameLeaderboardSet> entry : gameLeaderboards.entrySet()) {
                oos.writeLong(entry.getKey());
                oos.writeObject(entry.getValue());
            }
            // Atomic move to ensure consistency
            Files.move(tempSnapshotFilePath, snapshotFilePath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);

            logger.info("Moving WAL {} to archive", walFilePath);
            if (!Files.exists(walFilePath)) {
                Files.createFile(walFilePath);
            }
            Files.move(walFilePath, archivedWalFilePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            logger.info("Snapshot created");
        } catch (IOException e) {
            logger.error("Failed to create snapshot", e);
            throw new RuntimeException("Failed to create snapshot", e);
        } finally {
            try {
                Files.deleteIfExists(tempSnapshotFilePath);
            } catch (IOException cleanupEx) {
            }
        }
    }

    private long loadFromSnapshot(Path path) {
        if (!Files.exists(path)) {
            return 0;
        }
        FileTime snapshotFileTime = FileTime.fromMillis(0);
        try (ObjectInputStream ois = new ObjectInputStream(
                Files.newInputStream(path))) {
            snapshotFileTime = Files.getLastModifiedTime(path);
            while (true) {
                try {
                    long gameId = ois.readLong();
                    GameLeaderboardSet gameSet = (GameLeaderboardSet) ois.readObject();
                    gameSet.setExpiringScoresQueueRef(expiringScores);
                    gameLeaderboards.put(gameId, gameSet);
                    logger.info("Game Set: {} with {} leaderboards", gameId,
                            gameSet.getLeaderboard(null).getTotalPlayers());
                } catch (EOFException e) {
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to load snapshot", e);
        }
        logger.info("Game Set: {}", gameLeaderboards.size());
        return snapshotFileTime.toMillis();
    }

    private void replayWALFile(Path path, long fromTimestamp) {
        if (!Files.exists(path)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                long timestamp = Long.parseLong(parts[0]);
                if (timestamp < fromTimestamp) {
                    continue;
                }
                ScoreEntry entry = new ScoreEntry(
                        Long.parseLong(parts[2]), // userId
                        Long.parseLong(parts[1]), // gameId
                        Long.parseLong(parts[3]), // score
                        timestamp // timestamp
                );
                // Skip WAL writing when replaying
                GameLeaderboardSet gameSet = gameLeaderboards.get(entry.gameId());
                if (gameSet == null) {
                    gameSet = new GameLeaderboardSet(entry.gameId(), expiringScores);
                    gameLeaderboards.put(entry.gameId(), gameSet);
                }
                gameSet.addScore(entry);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to replay WAL", e);
        }
    }

    private void processExpiringScores() {
        while (isRunning) {
            try {
                ExpiringScore expired = expiringScores.take();
                GameLeaderboardSet gameSet = gameLeaderboards.get(expired.gameId());
                Leaderboard leaderboard = gameSet.getLeaderboard(expired.windowKey());
                leaderboard.removeScore(expired.scoreEntry());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public GameLeaderboardSet getGameLeaderboardSet(Long gameId) {
        return gameLeaderboards.get(gameId);
    }

    public static class ExpiringScore implements Delayed {
        private final ScoreEntry scoreEntry;
        private final long gameId;
        private final String windowKey;
        private final long expirationTime;

        public ExpiringScore(ScoreEntry entry, long gameId, String windowKey, long expirationTime) {
            this.scoreEntry = entry;
            this.gameId = gameId;
            this.windowKey = windowKey;
            this.expirationTime = expirationTime;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(expirationTime - System.currentTimeMillis(),
                    TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(expirationTime,
                    ((ExpiringScore) other).expirationTime);
        }

        public ScoreEntry scoreEntry() {
            return scoreEntry;
        }

        public long gameId() {
            return gameId;
        }

        public String windowKey() {
            return windowKey;
        }
    }
}