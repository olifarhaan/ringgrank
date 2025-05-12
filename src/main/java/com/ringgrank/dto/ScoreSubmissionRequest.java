package com.ringgrank.dto;

public record ScoreSubmissionRequest(long userId, long gameId, long score, long timestamp) {
}