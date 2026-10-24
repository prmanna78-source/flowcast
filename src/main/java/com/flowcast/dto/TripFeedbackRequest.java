package com.flowcast.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record TripFeedbackRequest(
        Long commuteId,
        String targetArrivalTime,
        String actualDepartureTime,
        String actualArrivalTime,
        Integer actualDurationMinutes,
        Boolean wasOnTime,
        Boolean wasDepartureUseful,
        String disruptionExperienced,
        String feedbackNotes
) {}
