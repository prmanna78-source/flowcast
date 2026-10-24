package com.flowcast.dto;

import java.time.Instant;
import java.util.List;

public record SourceStatusDto(
        String name,
        String status,
        boolean operational,
        Instant lastSuccessAt,
        Instant lastAttemptAt,
        String lastErrorCategory,
        Long latencyMs,
        Long dataFreshnessSeconds,
        String coverageRegion,
        List<String> capabilities,
        String setupInstructions
) {}
