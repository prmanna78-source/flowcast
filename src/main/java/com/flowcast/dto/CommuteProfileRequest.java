package com.flowcast.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommuteProfileRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 255) String originName,
        @DecimalMin("-90.0") @DecimalMax("90.0") double originLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") double originLongitude,
        @NotBlank @Size(max = 255) String destinationName,
        @DecimalMin("-90.0") @DecimalMax("90.0") double destinationLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") double destinationLongitude,
        @NotBlank String targetArrivalTime, // "HH:mm" e.g. "09:00"
        String weekdays, // "MON,TUE,WED,THU,FRI"
        Integer safetyBufferMinutes, // e.g. 15
        Boolean enabled
) {}
