package com.generador.horarios.proyecto.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.dashboard.dto.DashboardSummaryResponse;
import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.preference.PreferenceRepository;
import com.generador.horarios.proyecto.schedule.Schedule;
import com.generador.horarios.proyecto.schedule.ScheduleRepository;
import com.generador.horarios.proyecto.schedule.ScheduleStatus;
import com.generador.horarios.proyecto.schedule.engine.ConstraintViolation;
import com.generador.horarios.proyecto.schedule.engine.ScheduleValidator;
import com.generador.horarios.proyecto.schedule.engine.Severity;
import com.generador.horarios.proyecto.shift.ShiftAssignmentRepository;
import com.generador.horarios.proyecto.venue.CoverageRequirementRepository;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
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
class DashboardServiceTest {

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private PreferenceRepository preferenceRepository;

    @Mock
    private CoverageRequirementRepository coverageRequirementRepository;

    @Mock
    private ScheduleValidator scheduleValidator;

    private DashboardService dashboardService;
    private Venue venue;
    private int isoYear;
    private int isoWeek;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(venueRepository, employeeRepository, scheduleRepository,
                shiftAssignmentRepository, preferenceRepository, coverageRequirementRepository, scheduleValidator);

        venue = new Venue("Bar Test", LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(venue, "id", 1L);

        LocalDate today = LocalDate.now();
        isoYear = today.get(IsoFields.WEEK_BASED_YEAR);
        isoWeek = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private Employee activeEmployee(long id) {
        Employee employee = new Employee("Ana", "ana" + id + "@test.com", ContractType.FULL_TIME, null, venue);
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }

    @Test
    void rejectsWhenVenueNotFound() {
        when(venueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.getSummary(99L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void reportsNoScheduleWhenNoneExistsForTheCurrentWeek() {
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(employeeRepository.findByVenueIdAndActiveTrue(1L)).thenReturn(List.of(activeEmployee(10L), activeEmployee(11L)));
        when(scheduleRepository.findByVenueIdAndIsoYearAndIsoWeek(1L, isoYear, isoWeek)).thenReturn(Optional.empty());

        DashboardSummaryResponse response = dashboardService.getSummary(1L);

        assertThat(response.venueName()).isEqualTo("Bar Test");
        assertThat(response.employeeCount()).isEqualTo(2);
        assertThat(response.isoYear()).isEqualTo(isoYear);
        assertThat(response.isoWeek()).isEqualTo(isoWeek);
        assertThat(response.scheduleStatus()).isNull();
        assertThat(response.alerts()).hasSize(1);
        assertThat(response.alerts().get(0)).contains("No hay cuadrante generado");
    }

    @Test
    void reportsScheduleStatusAndEmptyAlertsWhenNoViolations() {
        Schedule schedule = new Schedule(venue, isoYear, isoWeek);
        ReflectionTestUtils.setField(schedule, "id", 900L);

        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(employeeRepository.findByVenueIdAndActiveTrue(1L)).thenReturn(List.of(activeEmployee(10L)));
        when(scheduleRepository.findByVenueIdAndIsoYearAndIsoWeek(1L, isoYear, isoWeek)).thenReturn(Optional.of(schedule));
        when(shiftAssignmentRepository.findByScheduleId(900L)).thenReturn(List.of());
        when(preferenceRepository.findByEmployeeIdIn(List.of(10L))).thenReturn(List.of());
        when(coverageRequirementRepository.findByVenueId(1L)).thenReturn(List.of());
        when(scheduleValidator.validate(anyList(), anyList(), anyList(), any())).thenReturn(List.of());

        DashboardSummaryResponse response = dashboardService.getSummary(1L);

        assertThat(response.scheduleStatus()).isEqualTo("DRAFT");
        assertThat(response.alerts()).isEmpty();
    }

    @Test
    void reportsAlertsFromCurrentValidationEvenIfScheduleWasAlreadyPublished() {
        Schedule schedule = new Schedule(venue, isoYear, isoWeek);
        ReflectionTestUtils.setField(schedule, "id", 900L);
        schedule.setStatus(ScheduleStatus.PUBLISHED);

        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(employeeRepository.findByVenueIdAndActiveTrue(1L)).thenReturn(List.of(activeEmployee(10L)));
        when(scheduleRepository.findByVenueIdAndIsoYearAndIsoWeek(1L, isoYear, isoWeek)).thenReturn(Optional.of(schedule));
        when(shiftAssignmentRepository.findByScheduleId(900L)).thenReturn(List.of());
        when(preferenceRepository.findByEmployeeIdIn(List.of(10L))).thenReturn(List.of());
        when(coverageRequirementRepository.findByVenueId(1L)).thenReturn(List.of());

        ConstraintViolation violation = new ConstraintViolation("H5", Severity.HARD,
                "Empleado marcado como no disponible el " + LocalDate.now(), 10L, LocalDate.now());
        when(scheduleValidator.validate(anyList(), anyList(), anyList(), any())).thenReturn(List.of(violation));

        DashboardSummaryResponse response = dashboardService.getSummary(1L);

        assertThat(response.scheduleStatus()).isEqualTo("PUBLISHED");
        assertThat(response.alerts()).containsExactly("Ana — " + violation.message());
    }
}
