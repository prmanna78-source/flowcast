package com.flowcast;

import static org.assertj.core.api.Assertions.assertThat;
import com.flowcast.dto.ApiResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiResponseTest {
    @Test
    void unavailableResponseHasNoDataAndExplainsReason() {
        ApiResponse<String> response = ApiResponse.unavailable("CONFIG_MISSING", "TomTom", "key required");
        assertThat(response.status()).isEqualTo("CONFIG_MISSING");
        assertThat(response.data()).isNull();
        assertThat(response.reason()).isEqualTo("key required");
        assertThat(response.retrievedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void loadDotEnvPopulatesSystemProperties() {
        FlowCastApplication.loadDotEnv();
        assertThat(System.getProperty("FLOWCAST_TOMTOM_SERVER_KEY")).isNotNull();
    }
}
