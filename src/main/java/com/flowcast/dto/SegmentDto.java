package com.flowcast.dto;

public record SegmentDto(String segmentId, double latitude, double longitude,
                         Double currentSpeedKmh, Double freeFlowSpeedKmh,
                         Integer currentTravelTimeSeconds, Integer freeFlowTravelTimeSeconds,
                         Integer confidence) {}
