package com.flowcast.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeparturePlanRequest(
        @NotBlank String originName,
        @DecimalMin("-90.0") @DecimalMax("90.0") double originLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") double originLongitude,
        @NotBlank String destinationName,
        @DecimalMin("-90.0") @DecimalMax("90.0") double destinationLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") double destinationLongitude,
        @NotBlank String arrivalTime, // "HH:mm" (24-hour format e.g. "09:00" or ISO time)
        String arrivalDate, // optional "YYYY-MM-DD", defaults to current date in Asia/Kolkata
        Integer safetyBufferMinutes // optional buffer in minutes (default 15)
) {}
