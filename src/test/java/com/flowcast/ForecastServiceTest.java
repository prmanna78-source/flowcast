package com.flowcast;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowcast.config.FlowCastProperties;
import com.flowcast.dto.ForecastReadinessDto;
import com.flowcast.service.ForecastService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ForecastServiceTest {
    private FlowCastProperties properties;
    private ForecastService service;

    @BeforeEach
    void setUp() {
        properties = new FlowCastProperties();
        properties.setTrafficHistoryEnabled(false);
        properties.setTrafficTrainingPermission(false);
        service = new ForecastService(properties, Optional.empty());
    }

    @Test
    void readinessExplainsPermissionRequiredWhenDisabled() {
        ForecastReadinessDto readiness = service.getReadiness();
        assertThat(readiness.isReady()).isFalse();
        assertThat(readiness.status()).isEqualTo("PERMISSION_OR_COLLECTION_DISABLED");
        assertThat(readiness.criteria()).hasSize(5);
    }

    @Test
    void readinessExplainsInsufficientObservationsWhenEnabled() {
        properties.setTrafficHistoryEnabled(true);
        properties.setTrafficTrainingPermission(true);
        properties.setMinTrainingSamples(100);

        service.incrementObservationCount(15);

        ForecastReadinessDto readiness = service.getReadiness();
        assertThat(readiness.isReady()).isFalse();
        assertThat(readiness.status()).isEqualTo("INSUFFICIENT_OBSERVATIONS");
        assertThat(readiness.observationsCount()).isEqualTo(15);
    }

    @Test
    void readinessBecomesReadyWhenObservationsExceedThreshold() {
        properties.setTrafficHistoryEnabled(true);
        properties.setTrafficTrainingPermission(true);
        properties.setMinTrainingSamples(50);

        service.incrementObservationCount(60);

        ForecastReadinessDto readiness = service.getReadiness();
        assertThat(readiness.isReady()).isTrue();
        assertThat(readiness.status()).isEqualTo("EVALUATED_AND_READY");
        assertThat(readiness.evaluatedMae()).isNotNull();
    }
}
