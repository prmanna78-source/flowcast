package com.flowcast.dto;

import java.time.Instant;
import java.util.List;

public record DisruptionAssessmentDto(
        String category,
        String confidence,
        Instant assessedAt,
        int activeIncidentsCount,
        int trafficDelaySeconds,
        double travelDurationMinutes,
        double distanceKm,
        String weatherSummary,
        List<IncidentDto> relevantIncidents,
        List<String> reasons,
        List<String> coverageLimitations,
        String recommendation
) {}
