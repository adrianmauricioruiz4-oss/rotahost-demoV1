package com.generador.horarios.proyecto.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.preference.dto.PreferenceRequest;
import com.generador.horarios.proyecto.preference.dto.PreferenceResponse;
import com.generador.horarios.proyecto.shift.ShiftSegment;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.shift.ShiftTemplateRepository;
import com.generador.horarios.proyecto.venue.Venue;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
class PreferenceServiceTest {

    @Mock
    private PreferenceRepository preferenceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ShiftTemplateRepository shiftTemplateRepository;

    private PreferenceService preferenceService;
    private Venue venue;
    private Venue otherVenue;
    private Employee employee;
    private ShiftTemplate shiftTemplate;

    @BeforeEach
    void setUp() {
        preferenceService = new PreferenceService(preferenceRepository, employeeRepository, shiftTemplateRepository);

        venue = new Venue("Bar Test", LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(venue, "id", 1L);

        otherVenue = new Venue("Otro Bar", LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(otherVenue, "id", 2L);

        employee = new Employee("Ana", "ana@test.com", ContractType.FULL_TIME, null, venue);
        ReflectionTestUtils.setField(employee, "id", 10L);

        shiftTemplate = new ShiftTemplate("TARDE", venue, List.of(new ShiftSegment(LocalTime.of(16, 0), LocalTime.MIDNIGHT)));
        ReflectionTestUtils.setField(shiftTemplate, "id", 100L);
    }

    @Test
    void createsDayPreference() {
        PreferenceRequest request = new PreferenceRequest(10L, PreferenceType.PREFERS_DAY, DayOfWeek.SUNDAY, null, null, 3);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(preferenceRepository.save(any(Preference.class))).thenAnswer(invocation -> {
            Preference saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 500L);
            return saved;
        });

        PreferenceResponse response = preferenceService.create(request);

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.dayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(response.weight()).isEqualTo(3);
    }

    @Test
    void createsShiftPreferenceWhenShiftTemplateBelongsToEmployeeVenue() {
        PreferenceRequest request = new PreferenceRequest(10L, PreferenceType.AVOIDS_SHIFT, null, 100L, null, 2);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(shiftTemplateRepository.findById(100L)).thenReturn(Optional.of(shiftTemplate));
        when(preferenceRepository.save(any(Preference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PreferenceResponse response = preferenceService.create(request);

        assertThat(response.shiftTemplateId()).isEqualTo(100L);
    }

    @Test
    void rejectsShiftTemplateFromAnotherVenue() {
        Employee employeeAtOtherVenue = new Employee("Bea", "bea@test.com", ContractType.FULL_TIME, null, otherVenue);
        ReflectionTestUtils.setField(employeeAtOtherVenue, "id", 20L);

        PreferenceRequest request = new PreferenceRequest(20L, PreferenceType.PREFERS_SHIFT, null, 100L, null, 4);
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(employeeAtOtherVenue));
        when(shiftTemplateRepository.findById(100L)).thenReturn(Optional.of(shiftTemplate));

        assertThatThrownBy(() -> preferenceService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no pertenece");
    }

    @Test
    void createsUnavailabilityWithoutWeightAndStoresZero() {
        PreferenceRequest request = new PreferenceRequest(
                10L, PreferenceType.UNAVAILABLE, null, null, LocalDate.of(2026, 8, 20), null);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(preferenceRepository.save(any(Preference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PreferenceResponse response = preferenceService.create(request);

        assertThat(response.specificDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(response.weight()).isZero();
    }

    @Test
    void rejectsWeightForUnavailable() {
        PreferenceRequest request = new PreferenceRequest(
                10L, PreferenceType.UNAVAILABLE, null, null, LocalDate.of(2026, 8, 20), 3);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> preferenceService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("weight");
    }

    @Test
    void rejectsMissingDayOfWeekForDayType() {
        PreferenceRequest request = new PreferenceRequest(10L, PreferenceType.AVOIDS_DAY, null, null, null, 2);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> preferenceService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dayOfWeek");
    }

    @Test
    void rejectsMissingWeightForSoftType() {
        PreferenceRequest request = new PreferenceRequest(10L, PreferenceType.PREFERS_DAY, DayOfWeek.MONDAY, null, null, null);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> preferenceService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("weight");
    }

    @Test
    void rejectsDayOfWeekPresentForShiftType() {
        PreferenceRequest request = new PreferenceRequest(10L, PreferenceType.PREFERS_SHIFT, DayOfWeek.MONDAY, 100L, null, 2);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(shiftTemplateRepository.findById(100L)).thenReturn(Optional.of(shiftTemplate));

        assertThatThrownBy(() -> preferenceService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("dayOfWeek");
    }

    @Test
    void findAllFiltersByEmployeeIdWhenProvided() {
        when(preferenceRepository.findByEmployeeId(10L)).thenReturn(List.of());

        preferenceService.findAll(10L);

        verify(preferenceRepository).findByEmployeeId(10L);
        verify(preferenceRepository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    void findAllReturnsEverythingWhenNoEmployeeIdGiven() {
        when(preferenceRepository.findAll()).thenReturn(List.of());

        preferenceService.findAll(null);

        verify(preferenceRepository).findAll();
        verify(preferenceRepository, org.mockito.Mockito.never()).findByEmployeeId(any());
    }

    @Test
    void deleteRemovesTheRowHard() {
        Preference existing = new Preference(employee, PreferenceType.PREFERS_DAY, DayOfWeek.MONDAY, null, null, 3);
        ReflectionTestUtils.setField(existing, "id", 500L);
        when(preferenceRepository.findById(500L)).thenReturn(Optional.of(existing));

        preferenceService.delete(500L);

        verify(preferenceRepository).delete(existing);
    }
}
