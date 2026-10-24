package com.flowcast.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public record DeparturePlanResponse(
        String originName,
        String destinationName,
        String targetArrivalTime, // e.g. "09:00 AM"
        String targetArrivalIso,  // e.g. "2026-10-25T09:00:00+05:30"
        String recommendedDepartureTime, // e.g. "08:15 AM"
        String recommendedDepartureIso,
        long minutesUntilDeparture,
        double travelDurationMinutes,
        int trafficDelayMinutes,
        double distanceKm,
        int safetyBufferMinutes,
        String journeyStatus, // "READY_TO_DEPART", "DEPARTURE_UPCOMING", "DEPARTURE_MISSED", "DISRUPTION_DETECTED"
        String statusMessage,
        String decisionSummary,
        List<String> evidenceFindings,
        List<String> uncertainties,
        String exposureCategory, // "LOWER_EXPOSURE", "MODERATE_EXPOSURE", "ELEVATED_EXPOSURE", "HIGH_EXPOSURE"
        String weatherSummary,
        int activeIncidentsCount,
        JsonNode routeSummary,
        JsonNode fullRouteData,
        Instant calculatedAt
) {}
