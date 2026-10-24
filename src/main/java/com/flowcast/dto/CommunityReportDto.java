package com.flowcast.dto;

import java.time.Instant;

public record CommunityReportDto(
        Long id,
        String reportType,
        String description,
        double latitude,
        double longitude,
        String status,
        Instant createdAt,
        Instant reviewedAt,
        String sourceLabel
) {}
