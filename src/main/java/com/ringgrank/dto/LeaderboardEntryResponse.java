package com.ringgrank.dto;

public record LeaderboardEntryResponse(long userId, long score, long timestamp, int rank) {
}