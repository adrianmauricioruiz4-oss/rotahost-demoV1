package com.generador.horarios.proyecto.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.shift.dto.ShiftSegmentRequest;
import com.generador.horarios.proyecto.shift.dto.ShiftTemplateRequest;
import com.generador.horarios.proyecto.shift.dto.ShiftTemplateResponse;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ShiftTemplateServiceTest {

    @Mock
    private ShiftTemplateRepository shiftTemplateRepository;

    @Mock
    private VenueRepository venueRepository;

    private ShiftTemplateService shiftTemplateService;
    private Venue venue;

    @BeforeEach
    void setUp() {
        shiftTemplateService = new ShiftTemplateService(shiftTemplateRepository, venueRepository);
        venue = new Venue("Bar Test", LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(venue, "id", 1L);
    }

    @Test
    void createsSingleSegmentShift() {
        ShiftTemplateRequest request = new ShiftTemplateRequest("MAÑANA", 1L,
                List.of(new ShiftSegmentRequest(LocalTime.of(8, 0), LocalTime.of(16, 0))));
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(shiftTemplateRepository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> {
            ShiftTemplate saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        ShiftTemplateResponse response = shiftTemplateService.create(request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.segments()).hasSize(1);
        assertThat(response.active()).isTrue();
    }

    @Test
    void createsSplitShiftCrossingMidnight() {
        ShiftTemplateRequest request = new ShiftTemplateRequest("PARTIDO", 1L, List.of(
                new ShiftSegmentRequest(LocalTime.of(12, 0), LocalTime.of(16, 0)),
                new ShiftSegmentRequest(LocalTime.of(20, 0), LocalTime.MIDNIGHT)));
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(shiftTemplateRepository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftTemplateResponse response = shiftTemplateService.create(request);

        assertThat(response.segments()).hasSize(2);
    }

    @Test
    void rejectsUnknownVenue() {
        ShiftTemplateRequest request = new ShiftTemplateRequest("MAÑANA", 99L,
                List.of(new ShiftSegmentRequest(LocalTime.of(8, 0), LocalTime.of(16, 0))));
        when(venueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shiftTemplateService.create(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsSegmentWhereEndIsBeforeStartAndNotMidnight() {
        ShiftTemplateRequest request = new ShiftTemplateRequest("RARO", 1L,
                List.of(new ShiftSegmentRequest(LocalTime.of(20, 0), LocalTime.of(18, 0))));
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));

        assertThatThrownBy(() -> shiftTemplateService.create(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsSegmentWithEqualStartAndEnd() {
        ShiftTemplateRequest request = new ShiftTemplateRequest("VACIO", 1L,
                List.of(new ShiftSegmentRequest(LocalTime.of(10, 0), LocalTime.of(10, 0))));
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));

        assertThatThrownBy(() -> shiftTemplateService.create(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsOverlappingSegments() {
        ShiftTemplateRequest request = new ShiftTemplateRequest("SOLAPADO", 1L, List.of(
                new ShiftSegmentRequest(LocalTime.of(12, 0), LocalTime.of(18, 0)),
                new ShiftSegmentRequest(LocalTime.of(16, 0), LocalTime.of(20, 0))));
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));

        assertThatThrownBy(() -> shiftTemplateService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("solapan");
    }

    @Test
    void softDeleteDeactivatesShiftTemplateWithoutRemovingIt() {
        ShiftTemplate existing = new ShiftTemplate("TARDE", venue,
                List.of(new ShiftSegment(LocalTime.of(16, 0), LocalTime.MIDNIGHT)));
        ReflectionTestUtils.setField(existing, "id", 100L);
        when(shiftTemplateRepository.findById(100L)).thenReturn(Optional.of(existing));

        shiftTemplateService.delete(100L);

        assertThat(existing.isActive()).isFalse();
    }
}
