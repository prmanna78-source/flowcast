package com.flowcast.dto;

import java.time.Instant;
import java.util.List;

public record ForecastReadinessDto(
        String status,
        boolean isReady,
        long observationsCount,
        long requiredObservationsThreshold,
        int samplingPointsCount,
        String observationTimeRange,
        Double evaluatedMae,
        Double baselineMae,
        String evaluationMethod,
        Instant lastTrainingAt,
        int supportedHorizonMinutes,
        List<String> criteria,
        List<String> limitations
) {}
