package com.flowcast;

import com.flowcast.dto.CommuteProfileDto;
import com.flowcast.dto.CommuteProfileRequest;
import com.flowcast.dto.DeparturePlanResponse;
import com.flowcast.dto.TripFeedbackDto;
import com.flowcast.dto.TripFeedbackRequest;
import com.flowcast.service.CommuteService;
import com.flowcast.service.DepartureRecommendationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CommuteServiceTest {

    private DepartureRecommendationService departureService;
    private CommuteService commuteService;

    @BeforeEach
    void setUp() {
        departureService = Mockito.mock(DepartureRecommendationService.class);
        commuteService = new CommuteService(departureService, Optional.empty());
    }

    @Test
    void commuteLifecycleSavesUpdatesTogglesAndRecalculates() {
        when(departureService.planDeparture(any())).thenReturn(new DeparturePlanResponse(
                "Home", "Office", "09:00 AM", "2026-10-25T09:00:00+05:30",
                "08:15 AM", "2026-10-25T08:15:00+05:30", 45, 30.0, 5, 12.0, 15,
                "DEPARTURE_UPCOMING", "On schedule", "Leave by 08:15 AM",
                List.of("Free flow"), List.of(), "LOWER_EXPOSURE", "Clear", 0, null, null, Instant.now()
        ));

        // 1. Save commute
        CommuteProfileRequest createReq = new CommuteProfileRequest(
                "College Commute", "NAD Junction", 17.7475, 83.2185,
                "Andhra University", 17.7315, 83.3185,
                "09:00", "MON,TUE,WED,THU,FRI", 15, true
        );
        CommuteProfileDto saved = commuteService.saveCommute(createReq);

        assertThat(saved).isNotNull();
        assertThat(saved.name()).isEqualTo("College Commute");
        assertThat(saved.lastDepartureRecommendation()).isEqualTo("08:15 AM");
        assertThat(saved.enabled()).isTrue();

        // 2. List commutes
        List<CommuteProfileDto> list = commuteService.listCommutes();
        assertThat(list).hasSize(1);

        // 3. Toggle enabled status
        Optional<CommuteProfileDto> toggled = commuteService.toggleCommute(saved.id());
        assertThat(toggled).isPresent();
        assertThat(toggled.get().enabled()).isFalse();

        // 4. Recalculate
        Optional<DeparturePlanResponse> recalculation = commuteService.recalculateCommute(saved.id());
        assertThat(recalculation).isPresent();
        assertThat(recalculation.get().recommendedDepartureTime()).isEqualTo("08:15 AM");

        // 5. Trip feedback
        TripFeedbackDto fb = commuteService.recordFeedback(new TripFeedbackRequest(
                saved.id(), "09:00", "08:15", "08:55", 40, true, true, "None", "Arrived smoothly"
        ));
        assertThat(fb).isNotNull();
        assertThat(fb.wasOnTime()).isTrue();
        assertThat(commuteService.listFeedback()).hasSize(1);

        // 6. Delete
        boolean deleted = commuteService.deleteCommute(saved.id());
        assertThat(deleted).isTrue();
        assertThat(commuteService.listCommutes()).isEmpty();
    }
}
