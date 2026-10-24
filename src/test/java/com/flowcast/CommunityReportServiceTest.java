package com.flowcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowcast.dto.CommunityReportDto;
import com.flowcast.dto.CommunityReportRequest;
import com.flowcast.service.CommunityReportService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommunityReportServiceTest {
    private CommunityReportService service;

    @BeforeEach
    void setUp() {
        service = new CommunityReportService(Optional.empty());
    }

    @Test
    void submitReportStoresAndRetrievesReport() {
        CommunityReportRequest req = new CommunityReportRequest(
                "Waterlogging", "Heavy water stagnation under Gajuwaka flyover.", 17.6868, 83.2185);

        CommunityReportDto created = service.submitReport(req, "192.168.1.1");
        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo("REPORTED");
        assertThat(created.reportType()).isEqualTo("Waterlogging");

        List<CommunityReportDto> reports = service.listReports();
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).id()).isEqualTo(created.id());
    }

    @Test
    void submitReportEnforcesRateLimitingPerClient() {
        CommunityReportRequest req = new CommunityReportRequest(
                "Pothole", "Large crater on left lane.", 17.7000, 83.2500);

        service.submitReport(req, "10.0.0.1");

        assertThatThrownBy(() -> service.submitReport(req, "10.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate-limited");
    }
}
