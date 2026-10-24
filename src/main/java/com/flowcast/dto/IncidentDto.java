package com.flowcast.dto;

import java.util.List;

public record IncidentDto(
        String id,
        String category,
        String description,
        String roadName,
        String severity,
        List<List<Double>> geometry,
        Integer delaySeconds,
        String startTime,
        String endTime,
        String status,
        String source
) {
    public IncidentDto(String id, String category, String description, String roadName, String severity, List<List<Double>> geometry) {
        this(id, category, description, roadName, severity, geometry, null, null, null, "ACTIVE", "TomTom Orbis");
    }
}
