package com.generador.horarios.proyecto.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.employee.Position;
import com.generador.horarios.proyecto.shift.ShiftSegment;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.shift.ShiftTemplateRepository;
import com.generador.horarios.proyecto.venue.dto.CoverageRequirementRequest;
import com.generador.horarios.proyecto.venue.dto.CoverageRequirementResponse;
import java.time.DayOfWeek;
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
class CoverageRequirementServiceTest {

    @Mock
    private CoverageRequirementRepository coverageRequirementRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private ShiftTemplateRepository shiftTemplateRepository;

    private CoverageRequirementService coverageRequirementService;
    private Venue venue;
    private Venue otherVenue;
    private ShiftTemplate shiftTemplate;

    @BeforeEach
    void setUp() {
        coverageRequirementService =
                new CoverageRequirementService(coverageRequirementRepository, venueRepository, shiftTemplateRepository);

        venue = new Venue("Bar Test", LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(venue, "id", 1L);

        otherVenue = new Venue("Otro Bar", LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(otherVenue, "id", 2L);

        shiftTemplate = new ShiftTemplate("TARDE", venue,
                List.of(new ShiftSegment(LocalTime.of(16, 0), LocalTime.MIDNIGHT)));
        ReflectionTestUtils.setField(shiftTemplate, "id", 100L);
    }

    @Test
    void createsCoverageRequirement() {
        CoverageRequirementRequest request = new CoverageRequirementRequest(1L, DayOfWeek.FRIDAY, 100L, 3, null);
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(shiftTemplateRepository.findById(100L)).thenReturn(Optional.of(shiftTemplate));
        when(coverageRequirementRepository.findByVenueIdAndDayOfWeekAndShiftTemplateId(1L, DayOfWeek.FRIDAY, 100L))
                .thenReturn(List.of());
        when(coverageRequirementRepository.save(any(CoverageRequirement.class))).thenAnswer(invocation -> {
            CoverageRequirement saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 500L);
            return saved;
        });

        CoverageRequirementResponse response = coverageRequirementService.create(request);

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.requiredCount()).isEqualTo(3);
        assertThat(response.position()).isNull();
    }

    @Test
    void rejectsShiftTemplateFromAnotherVenue() {
        CoverageRequirementRequest request = new CoverageRequirementRequest(2L, DayOfWeek.FRIDAY, 100L, 3, null);
        when(venueRepository.findById(2L)).thenReturn(Optional.of(otherVenue));
        when(shiftTemplateRepository.findById(100L)).thenReturn(Optional.of(shiftTemplate));

        assertThatThrownBy(() -> coverageRequirementService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no pertenece");
    }

    @Test
    void rejectsInactiveShiftTemplate() {
        shiftTemplate.setActive(false);
        CoverageRequirementRequest request = new CoverageRequirementRequest(1L, DayOfWeek.FRIDAY, 100L, 3, null);
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(shiftTemplateRepository.findById(100L)).thenReturn(Optional.of(shiftTemplate));

        assertThatThrownBy(() -> coverageRequirementService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("desactivado");
    }

    @Test
    void rejectsDuplicateCombinationOnCreate() {
        CoverageRequirementRequest request = new CoverageRequirementRequest(1L, DayOfWeek.FRIDAY, 100L, 3, null);
        CoverageRequirement existing = new CoverageRequirement(venue, DayOfWeek.FRIDAY, shiftTemplate, 2);
        ReflectionTestUtils.setField(existing, "id", 500L);

        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(shiftTemplateRepository.findById(100L)).thenReturn(Optional.of(shiftTemplate));
        when(coverageRequirementRepository.findByVenueIdAndDayOfWeekAndShiftTemplateId(1L, DayOfWeek.FRIDAY, 100L))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> coverageRequirementService.create(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void allowsDifferentPositionsForSameDayAndShift() {
        CoverageRequirementRequest request = new CoverageRequirementRequest(1L, DayOfWeek.FRIDAY, 100L, 2, Position.COCINERO);
        CoverageRequirement existingForCamarero = new CoverageRequirement(venue, DayOfWeek.FRIDAY, shiftTemplate, 3);
        existingForCamarero.setPosition(Position.CAMARERO);
        ReflectionTestUtils.setField(existingForCamarero, "id", 500L);

        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(shiftTemplateRepository.findById(100L)).thenReturn(Optional.of(shiftTemplate));
        when(coverageRequirementRepository.findByVenueIdAndDayOfWeekAndShiftTemplateId(1L, DayOfWeek.FRIDAY, 100L))
                .thenReturn(List.of(existingForCamarero));
        when(coverageRequirementRepository.save(any(CoverageRequirement.class))).thenAnswer(invocation -> {
            CoverageRequirement saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 501L);
            return saved;
        });

        CoverageRequirementResponse response = coverageRequirementService.create(request);

        assertThat(response.position()).isEqualTo(Position.COCINERO);
    }

    @Test
    void rejectsDuplicateSamePositionOnCreate() {
        CoverageRequirementRequest request = new CoverageRequirementRequest(1L, DayOfWeek.FRIDAY, 100L, 2, Position.CAMARERO);
        CoverageRequirement existing = new CoverageRequirement(venue, DayOfWeek.FRIDAY, shiftTemplate, 3);
        existing.setPosition(Position.CAMARERO);
        ReflectionTestUtils.setField(existing, "id", 500L);

        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(shiftTemplateRepository.findById(100L)).thenReturn(Optional.of(shiftTemplate));
        when(coverageRequirementRepository.findByVenueIdAndDayOfWeekAndShiftTemplateId(1L, DayOfWeek.FRIDAY, 100L))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> coverageRequirementService.create(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void allowsUpdatingItsOwnCombination() {
        CoverageRequirement existing = new CoverageRequirement(venue, DayOfWeek.FRIDAY, shiftTemplate, 2);
        ReflectionTestUtils.setField(existing, "id", 500L);
        CoverageRequirementRequest request = new CoverageRequirementRequest(1L, DayOfWeek.FRIDAY, 100L, 5, null);

        when(coverageRequirementRepository.findById(500L)).thenReturn(Optional.of(existing));
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(shiftTemplateRepository.findById(100L)).thenReturn(Optional.of(shiftTemplate));
        when(coverageRequirementRepository.findByVenueIdAndDayOfWeekAndShiftTemplateId(1L, DayOfWeek.FRIDAY, 100L))
                .thenReturn(List.of(existing));

        CoverageRequirementResponse response = coverageRequirementService.update(500L, request);

        assertThat(response.requiredCount()).isEqualTo(5);
    }

    @Test
    void deleteRemovesTheRowHard() {
        CoverageRequirement existing = new CoverageRequirement(venue, DayOfWeek.FRIDAY, shiftTemplate, 2);
        ReflectionTestUtils.setField(existing, "id", 500L);
        when(coverageRequirementRepository.findById(500L)).thenReturn(Optional.of(existing));

        coverageRequirementService.delete(500L);

        verify(coverageRequirementRepository).delete(existing);
    }
}
