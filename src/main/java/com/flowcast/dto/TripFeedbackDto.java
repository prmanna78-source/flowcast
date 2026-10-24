package com.flowcast.dto;

import java.time.Instant;

public record TripFeedbackDto(
        Long id,
        Long commuteId,
        String targetArrivalTime,
        String actualDepartureTime,
        String actualArrivalTime,
        Integer actualDurationMinutes,
        Boolean wasOnTime,
        Boolean wasDepartureUseful,
        String disruptionExperienced,
        String feedbackNotes,
        Instant createdAt
) {}
