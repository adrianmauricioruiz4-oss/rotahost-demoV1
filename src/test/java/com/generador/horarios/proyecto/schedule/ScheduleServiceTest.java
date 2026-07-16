package com.generador.horarios.proyecto.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.preference.PreferenceRepository;
import com.generador.horarios.proyecto.schedule.dto.GenerateScheduleRequest;
import com.generador.horarios.proyecto.schedule.dto.ScheduleGenerationResponse;
import com.generador.horarios.proyecto.schedule.engine.ConstraintViolation;
import com.generador.horarios.proyecto.schedule.engine.GenerationResult;
import com.generador.horarios.proyecto.schedule.engine.ScheduleGenerator;
import com.generador.horarios.proyecto.schedule.engine.ScheduleValidator;
import com.generador.horarios.proyecto.schedule.engine.Severity;
import com.generador.horarios.proyecto.shift.ShiftAssignment;
import com.generador.horarios.proyecto.shift.ShiftAssignmentRepository;
import com.generador.horarios.proyecto.shift.ShiftSegment;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.venue.CoverageRequirementRepository;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private CoverageRequirementRepository coverageRequirementRepository;

    @Mock
    private PreferenceRepository preferenceRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private ScheduleGenerator scheduleGenerator;

    @Mock
    private ScheduleValidator scheduleValidator;

    private ScheduleService scheduleService;
    private Venue venue;
    private ShiftTemplate manana;

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(scheduleRepository, venueRepository, employeeRepository,
                coverageRequirementRepository, preferenceRepository, shiftAssignmentRepository,
                scheduleGenerator, scheduleValidator);

        venue = new Venue("Bar Test", LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(venue, "id", 1L);

        manana = new ShiftTemplate("MAÑANA", venue, List.of(new ShiftSegment(LocalTime.of(8, 0), LocalTime.of(16, 0))));
        ReflectionTestUtils.setField(manana, "id", 100L);
    }

    @Test
    void generatesAndPersistsScheduleWhenNoHardViolations() {
        GenerateScheduleRequest request = new GenerateScheduleRequest(1L, 2026, 29);
        Employee employee = new Employee("Ana", "ana@test.com", ContractType.FULL_TIME, null, venue);
        ReflectionTestUtils.setField(employee, "id", 10L);

        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(scheduleRepository.existsByVenueIdAndIsoYearAndIsoWeek(1L, 2026, 29)).thenReturn(false);
        when(employeeRepository.findByVenueIdAndActiveTrue(1L)).thenReturn(List.of(employee));
        when(coverageRequirementRepository.findByVenueId(1L)).thenReturn(List.of());
        when(preferenceRepository.findByEmployeeIdIn(List.of(10L))).thenReturn(List.of());
        when(shiftAssignmentRepository.findBySchedule_Venue_IdAndDateBetween(eq(1L), any(), any())).thenReturn(List.of());

        ShiftAssignment assignment = new ShiftAssignment(employee, manana, null, LocalDate.of(2026, 7, 13));
        ReflectionTestUtils.setField(assignment, "id", 500L);
        GenerationResult generationResult = new GenerationResult(List.of(assignment), List.of(), List.of());
        when(scheduleGenerator.generate(any(), anyList(), anyList(), anyList(), anyList(), any())).thenReturn(generationResult);
        when(scheduleValidator.validate(anyList(), anyList(), anyList(), any())).thenReturn(List.of());
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> {
            Schedule saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 900L);
            return saved;
        });

        ScheduleGenerationResponse response = scheduleService.generate(request);

        assertThat(response.scheduleId()).isEqualTo(900L);
        assertThat(response.venueId()).isEqualTo(1L);
        assertThat(response.isoYear()).isEqualTo(2026);
        assertThat(response.isoWeek()).isEqualTo(29);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.assignments()).hasSize(1);
        assertThat(response.assignments().get(0).employeeId()).isEqualTo(10L);
        verify(shiftAssignmentRepository).saveAll(List.of(assignment));
    }

    @Test
    void rejectsWhenVenueNotFound() {
        GenerateScheduleRequest request = new GenerateScheduleRequest(99L, 2026, 29);
        when(venueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scheduleService.generate(request)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsWhenScheduleAlreadyExistsForThatWeek() {
        GenerateScheduleRequest request = new GenerateScheduleRequest(1L, 2026, 29);
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(scheduleRepository.existsByVenueIdAndIsoYearAndIsoWeek(1L, 2026, 29)).thenReturn(true);

        assertThatThrownBy(() -> scheduleService.generate(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ya existe");

        verify(scheduleGenerator, never()).generate(any(), anyList(), anyList(), anyList(), anyList(), any());
    }

    @Test
    void rejectsWhenValidatorFindsHardViolationAndPersistsNothing() {
        GenerateScheduleRequest request = new GenerateScheduleRequest(1L, 2026, 29);
        Employee employee = new Employee("Ana", "ana@test.com", ContractType.FULL_TIME, null, venue);
        ReflectionTestUtils.setField(employee, "id", 10L);

        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(scheduleRepository.existsByVenueIdAndIsoYearAndIsoWeek(1L, 2026, 29)).thenReturn(false);
        when(employeeRepository.findByVenueIdAndActiveTrue(1L)).thenReturn(List.of(employee));
        when(coverageRequirementRepository.findByVenueId(1L)).thenReturn(List.of());
        when(preferenceRepository.findByEmployeeIdIn(List.of(10L))).thenReturn(List.of());
        when(shiftAssignmentRepository.findBySchedule_Venue_IdAndDateBetween(eq(1L), any(), any())).thenReturn(List.of());

        ShiftAssignment assignment = new ShiftAssignment(employee, manana, null, LocalDate.of(2026, 7, 13));
        GenerationResult generationResult = new GenerationResult(List.of(assignment), List.of(), List.of());
        when(scheduleGenerator.generate(any(), anyList(), anyList(), anyList(), anyList(), any())).thenReturn(generationResult);

        ConstraintViolation hardViolation = new ConstraintViolation("H1", Severity.HARD, "boom", 10L, LocalDate.of(2026, 7, 13));
        when(scheduleValidator.validate(anyList(), anyList(), anyList(), any())).thenReturn(List.of(hardViolation));

        assertThatThrownBy(() -> scheduleService.generate(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("boom");

        verify(scheduleRepository, never()).save(any());
        verify(shiftAssignmentRepository, never()).saveAll(anyList());
    }

    @Test
    void derivesWeekStartConsistentWithGivenIsoYearAndWeek() {
        GenerateScheduleRequest request = new GenerateScheduleRequest(1L, 2026, 29);
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(scheduleRepository.existsByVenueIdAndIsoYearAndIsoWeek(1L, 2026, 29)).thenReturn(false);
        when(employeeRepository.findByVenueIdAndActiveTrue(1L)).thenReturn(List.of());
        when(coverageRequirementRepository.findByVenueId(1L)).thenReturn(List.of());
        when(preferenceRepository.findByEmployeeIdIn(List.of())).thenReturn(List.of());
        when(shiftAssignmentRepository.findBySchedule_Venue_IdAndDateBetween(eq(1L), any(), any())).thenReturn(List.of());
        when(scheduleGenerator.generate(any(), anyList(), anyList(), anyList(), anyList(), any()))
                .thenReturn(new GenerationResult(List.of(), List.of(), List.of()));
        when(scheduleValidator.validate(anyList(), anyList(), anyList(), any())).thenReturn(List.of());
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        scheduleService.generate(request);

        ArgumentCaptor<LocalDate> weekStartCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(scheduleGenerator).generate(any(), anyList(), anyList(), anyList(), anyList(), weekStartCaptor.capture());
        LocalDate weekStart = weekStartCaptor.getValue();

        assertThat(weekStart.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(weekStart.get(IsoFields.WEEK_BASED_YEAR)).isEqualTo(2026);
        assertThat(weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)).isEqualTo(29);
    }
}
