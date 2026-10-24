package com.flowcast.controller;

import com.flowcast.dto.ApiResponse;
import com.flowcast.dto.SourceStatusDto;
import com.flowcast.service.ProviderService;
import com.flowcast.service.SourceHealthService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StatusController {
    private final ProviderService service;
    private final SourceHealthService healthService;

    public StatusController(ProviderService service, SourceHealthService healthService) {
        this.service = service;
        this.healthService = healthService;
    }

    @GetMapping("/sources")
    public ApiResponse<List<SourceStatusDto>> sourcesList() {
        return ApiResponse.live("FlowCast Source Health Monitor", healthService.checkSources(), Instant.now());
    }

    @PostMapping("/sources/refresh")
    public ApiResponse<List<SourceStatusDto>> sourcesRefresh() {
        return ApiResponse.live("FlowCast Source Health Monitor (Refreshed)", healthService.checkSources(), Instant.now());
    }

    @GetMapping("/status/summary")
    public ApiResponse<Map<String, Object>> summary() {
        var p = service.properties();
        return ApiResponse.live("FlowCast", Map.of(
                "location", p.getLocationName(),
                "tomtomConfigured", !p.getTomtomServerKey().isBlank(),
                "weatherEnabled", p.isOpenMeteoEnabled(),
                "trafficHistoryEnabled", p.isTrafficHistoryEnabled(),
                "trainingEnabled", p.isTrafficTrainingPermission()), Instant.now());
    }

    @GetMapping("/status/sources")
    public ApiResponse<Map<String, String>> legacySources() {
        var p = service.properties();
        return ApiResponse.live("FlowCast", Map.of(
                "TomTom", p.getTomtomServerKey().isBlank() ? "CONFIG_MISSING" : "CONFIGURED",
                "Open-Meteo", p.isOpenMeteoEnabled() ? "ENABLED" : "DISABLED"), Instant.now());
    }
}
