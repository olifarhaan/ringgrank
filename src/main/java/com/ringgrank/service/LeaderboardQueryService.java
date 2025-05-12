package com.ringgrank.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ringgrank.dto.LeaderboardEntryResponse;
import com.ringgrank.dto.UserRankResponse;
import com.ringgrank.exception.GameNotFoundException;
import com.ringgrank.exception.UserNotFoundInLeaderboardException;
import com.ringgrank.model.GameLeaderboardSet;
import com.ringgrank.model.Leaderboard;
import com.ringgrank.model.ScoreEntry;

@Service
public class LeaderboardQueryService {
    private final GlobalLeaderboardManager leaderboardManager;

    @Autowired
    public LeaderboardQueryService(GlobalLeaderboardManager leaderboardManager) {
        this.leaderboardManager = leaderboardManager;
    }

    public List<LeaderboardEntryResponse> getTopKLeaders(long gameId, int limit, String window) {
        GameLeaderboardSet gameSet = getGameLeaderboardSet(gameId);
        Leaderboard leaderboard = gameSet.getLeaderboard(window);

        List<ScoreEntry> topK = leaderboard.getTopK(limit);
        int rank = 1;

        List<LeaderboardEntryResponse> responses = new ArrayList<>();
        for (ScoreEntry entry : topK) {
            responses.add(new LeaderboardEntryResponse(
                    entry.userId(),
                    entry.score(),
                    entry.timestamp(),
                    rank));
            rank++;
        }
        return responses;
    }

    public UserRankResponse getUserRank(long gameId, long userId, String window) {
        GameLeaderboardSet gameSet = getGameLeaderboardSet(gameId);
        Leaderboard leaderboard = gameSet.getLeaderboard(window);

        ScoreEntry userScore = leaderboard.getUserScore(userId);
        if (userScore == null) {
            throw new UserNotFoundInLeaderboardException(
                    "User " + userId + " not found in leaderboard for game " + gameId);
        }

        int rank = leaderboard.getUserRank(userId);
        int totalPlayers = leaderboard.getTotalPlayers();
        double percentile = calculatePercentile(rank, totalPlayers);

        return new UserRankResponse(
                userId,
                rank,
                userScore.score(),
                percentile,
                userScore.timestamp());
    }

    private GameLeaderboardSet getGameLeaderboardSet(long gameId) {
        GameLeaderboardSet gameSet = leaderboardManager.getGameLeaderboardSet(gameId);
        if (gameSet == null) {
            throw new GameNotFoundException("Game " + gameId + " not found");
        }
        return gameSet;
    }

    private double calculatePercentile(int rank, int totalPlayers) {
        if (totalPlayers == 0)
            return 0.0;
        return ((totalPlayers - rank + 1) * 100.0) / totalPlayers;
    }
}