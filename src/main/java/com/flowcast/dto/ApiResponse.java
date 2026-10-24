package com.flowcast.dto;

import java.time.Instant;

public record ApiResponse<T>(
        String status,
        String source,
        Instant retrievedAt,
        Instant observedAt,
        Long freshnessSeconds,
        T data,
        String reason) {
    public static <T> ApiResponse<T> live(String source, T data, Instant retrievedAt) {
        return new ApiResponse<>("LIVE", source, retrievedAt, null, 0L, data, null);
    }
    public static <T> ApiResponse<T> unavailable(String status, String source, String reason) {
        return new ApiResponse<>(status, source, Instant.now(), null, null, null, reason);
    }
}
