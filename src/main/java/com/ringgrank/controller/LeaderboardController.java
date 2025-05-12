package com.ringgrank.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ringgrank.dto.LeaderboardEntryResponse;
import com.ringgrank.dto.UserRankResponse;
import com.ringgrank.service.LeaderboardQueryService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Controller for handling leaderboard queries.
 * All IDs (gameId, userId) are expected to be numeric.
 */
@RestController
@RequestMapping("/api/v1/games/{gameId}") // Base path includes gameId
@Validated // Enables validation for path variables and request parameters at the method
           // level
public class LeaderboardController {

    private final LeaderboardQueryService leaderboardQueryService;

    // Define constants for validation limits to maintain consistency and ease of
    // modification.
    private static final int MAX_LEADERBOARD_LIMIT = 1000;
    private static final int MIN_LEADERBOARD_LIMIT = 1;
    private static final long MIN_ID_VALUE = 1L; // Game and User IDs must be positive.
    private static final String WINDOW_REGEX = "^([1-9][0-9]*[hmMdsS])?$"; // Allows formats like "24h", "7d", "30m", or
                                                                           // empty for all-time.

    /**
     * Constructor for LeaderboardController.
     * 
     * @param leaderboardQueryService Service to handle leaderboard data retrieval.
     */
    @Autowired
    public LeaderboardController(LeaderboardQueryService leaderboardQueryService) {
        this.leaderboardQueryService = leaderboardQueryService;
    }

    /**
     * Endpoint to get the top K leaders for a game.
     * Supports all-time leaderboards and sliding-window leaderboards.
     * 
     * @param gameId The numeric ID of the game. Must be a positive number.
     * @param limit  The number of top players to return (K). Must be between
     *               MIN_LEADERBOARD_LIMIT and MAX_LEADERBOARD_LIMIT.
     * @param window Optional sliding window duration (e.g., "24h"). If not
     *               provided, returns all-time leaderboard.
     * @return ResponseEntity containing a list of top K players or an appropriate
     *         error response.
     */
    @GetMapping("/leaders")
    public ResponseEntity<List<LeaderboardEntryResponse>> getTopKLeaders(
            @PathVariable @Min(value = MIN_ID_VALUE, message = "Game ID must be a positive number.") Long gameId,

            @RequestParam(defaultValue = "10") @Min(value = MIN_LEADERBOARD_LIMIT, message = "Limit must be at least "
                    + MIN_LEADERBOARD_LIMIT + ".") @Max(value = MAX_LEADERBOARD_LIMIT, message = "Limit cannot exceed "
                            + MAX_LEADERBOARD_LIMIT + ".") int limit,

            @RequestParam(required = false) @Pattern(regexp = WINDOW_REGEX, message = "Window format is invalid. Examples: '24h', '7d', '30m'. Leave empty for all-time leaderboard.") String window) {

        List<LeaderboardEntryResponse> leaders = leaderboardQueryService.getTopKLeaders(gameId, limit, window);
        return ResponseEntity.ok(leaders);
    }

    /**
     * Endpoint to get a player's rank and percentile in a game.
     * Supports all-time leaderboards and sliding-window leaderboards.
     * 
     * @param gameId The numeric ID of the game. Must be a positive number.
     * @param userId The numeric ID of the user. Must be a positive number.
     * @param window Optional sliding window duration (e.g., "24h"). If not
     *               provided, returns all-time leaderboard.
     * @return ResponseEntity containing the user's rank and percentile or an
     *         appropriate error response.
     */
    @GetMapping("/users/{userId}/rank")
    public ResponseEntity<UserRankResponse> getUserRank(
            @PathVariable @Min(value = MIN_ID_VALUE, message = "Game ID must be a positive number.") Long gameId,

            @PathVariable @Min(value = MIN_ID_VALUE, message = "User ID must be a positive number.") Long userId,

            @RequestParam(required = false) @Pattern(regexp = WINDOW_REGEX, message = "Window format is invalid. Examples: '24h', '7d', '30m'. Leave empty for all-time leaderboard.") String window) {

        UserRankResponse rank = leaderboardQueryService.getUserRank(gameId, userId, window);
        return ResponseEntity.ok(rank);
    }
}