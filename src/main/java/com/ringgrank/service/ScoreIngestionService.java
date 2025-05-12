package com.ringgrank.service;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ringgrank.dto.ScoreSubmissionRequest;
import com.ringgrank.exception.InvalidScoreException;
import com.ringgrank.model.ScoreEntry;

@Service
public class ScoreIngestionService {
    private final GlobalLeaderboardManager leaderboardManager;

    @Autowired
    public ScoreIngestionService(GlobalLeaderboardManager leaderboardManager) {
        this.leaderboardManager = leaderboardManager;
    }

    public void processScore(ScoreSubmissionRequest request) {
        validateScore(request);

        ScoreEntry entry = new ScoreEntry(
                request.userId(),
                request.gameId(),
                request.score(),
                request.timestamp());

        leaderboardManager.recordScore(entry);
    }

    private void validateScore(ScoreSubmissionRequest request) {
        if (request.timestamp() > Instant.now().toEpochMilli()) {
            throw new InvalidScoreException("Score timestamp cannot be in the future");
        }
        if (request.score() < 0) {
            throw new InvalidScoreException("Score cannot be negative");
        }
    }
}