package com.flowcast.dto;

import java.time.Instant;

public record CommuteProfileDto(
        Long id,
        String name,
        String originName,
        double originLatitude,
        double originLongitude,
        String destinationName,
        double destinationLatitude,
        double destinationLongitude,
        String targetArrivalTime,
        String weekdays,
        int safetyBufferMinutes,
        boolean enabled,
        Instant lastCalculatedAt,
        String lastDepartureRecommendation,
        Double lastEstimatedDurationMinutes,
        Integer lastTrafficDelayMinutes,
        String lastExposureCategory,
        String journeyStatus,
        Instant createdAt
) {}
