package com.ringgrank.dto;

public record UserRankResponse(long userId, int rank, long score, double percentile, long timestamp) {
}