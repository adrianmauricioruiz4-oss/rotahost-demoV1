package com.generador.horarios.proyecto.shared;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.schedule.Schedule;
import com.generador.horarios.proyecto.schedule.ScheduleRepository;
import com.generador.horarios.proyecto.schedule.engine.ConstraintViolation;
import com.generador.horarios.proyecto.schedule.engine.GenerationResult;
import com.generador.horarios.proyecto.schedule.engine.ScheduleGenerator;
import com.generador.horarios.proyecto.schedule.engine.ScheduleValidator;
import com.generador.horarios.proyecto.schedule.engine.Severity;
import com.generador.horarios.proyecto.shift.ShiftAssignmentRepository;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.shift.ShiftTemplateRepository;
import com.generador.horarios.proyecto.venue.CoverageRequirement;
import com.generador.horarios.proyecto.venue.CoverageRequirementRepository;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private ShiftTemplateRepository shiftTemplateRepository;

    @Mock
    private CoverageRequirementRepository coverageRequirementRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private ScheduleGenerator scheduleGenerator;

    @Mock
    private ScheduleValidator scheduleValidator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Captor
    private ArgumentCaptor<List<CoverageRequirement>> coverageCaptor;

    @Captor
    private ArgumentCaptor<List<Employee>> employeeCaptor;

    private DemoDataSeeder newSeeder() {
        return new DemoDataSeeder(
                venueRepository, shiftTemplateRepository, coverageRequirementRepository, employeeRepository,
                scheduleRepository, shiftAssignmentRepository, scheduleGenerator, scheduleValidator, passwordEncoder);
    }

    @Test
    void seedsVenueShiftTemplatesCoverageAndFiveEmployeesWhenEmpty() {
        DemoDataSeeder seeder = newSeeder();

        when(venueRepository.count()).thenReturn(0L);
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftTemplateRepository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coverageRequirementRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(scheduleGenerator.generate(any(), anyList(), anyList(), anyList(), anyList(), any()))
                .thenReturn(new GenerationResult(List.of(), List.of(), List.of()));
        when(scheduleValidator.validate(anyList(), anyList(), anyList(), any())).thenReturn(List.of());
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run();

        verify(venueRepository).save(any(Venue.class));
        verify(shiftTemplateRepository, times(3)).save(any(ShiftTemplate.class));

        verify(coverageRequirementRepository).saveAll(coverageCaptor.capture());
        // MAÑANA + TARDE los 7 días, más un PARTIDO de refuerzo viernes y sábado.
        Assertions.assertThat(coverageCaptor.getValue()).hasSize(16);

        verify(employeeRepository).saveAll(employeeCaptor.capture());
        Assertions.assertThat(employeeCaptor.getValue()).hasSize(5);

        verify(scheduleRepository).save(any(Schedule.class));
        verify(shiftAssignmentRepository).saveAll(anyList());
    }

    /**
     * La demo pedía 200h semanales a un equipo de 170h contratadas, así que 4 turnos
     * quedaban sin cubrir todas las semanas por pura aritmética y los tres fijos salían
     * clavados en su máximo legal. No era un fallo del generador: era la cobertura de
     * demo pidiendo horas que no existen. Este test fija la relación.
     */
    @Test
    void demoCoverageFitsWithinContractedHours() {
        DemoDataSeeder seeder = newSeeder();

        when(venueRepository.count()).thenReturn(0L);
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftTemplateRepository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coverageRequirementRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(scheduleGenerator.generate(any(), anyList(), anyList(), anyList(), anyList(), any()))
                .thenReturn(new GenerationResult(List.of(), List.of(), List.of()));
        when(scheduleValidator.validate(anyList(), anyList(), anyList(), any())).thenReturn(List.of());
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run();

        verify(coverageRequirementRepository).saveAll(coverageCaptor.capture());
        verify(employeeRepository).saveAll(employeeCaptor.capture());

        double demandedHours = coverageCaptor.getValue().stream()
                .mapToDouble(requirement -> requirement.getRequiredCount() * shiftHours(requirement.getShiftTemplate()))
                .sum();
        int contractedHours = employeeCaptor.getValue().stream()
                .mapToInt(employee -> employee.getContractType() == ContractType.PART_TIME
                        && employee.getContractHours() != null
                        ? employee.getContractHours()
                        : 40)
                .sum();

        Assertions.assertThat(demandedHours)
                .as("la cobertura de demo (%sh) debe caber en las horas contratadas (%sh), "
                        + "o el cuadrante de demo nace con huecos imposibles de cubrir",
                        demandedHours, contractedHours)
                .isLessThanOrEqualTo(contractedHours);
    }

    /** Horas efectivas de un turno, sumando sus segmentos y tratando 00:00 como fin de día. */
    private double shiftHours(ShiftTemplate template) {
        return template.getSegments().stream()
                .mapToInt(segment -> {
                    int start = segment.getStartTime().toSecondOfDay() / 60;
                    int end = segment.getEndTime().equals(LocalTime.MIDNIGHT)
                            ? 24 * 60
                            : segment.getEndTime().toSecondOfDay() / 60;
                    return end - start;
                })
                .sum() / 60.0;
    }

    @Test
    void skipsSeedingScheduleWhenGeneratorLeavesAHardViolation() {
        DemoDataSeeder seeder = newSeeder();

        when(venueRepository.count()).thenReturn(0L);
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftTemplateRepository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(coverageRequirementRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(scheduleGenerator.generate(any(), anyList(), anyList(), anyList(), anyList(), any()))
                .thenReturn(new GenerationResult(List.of(), List.of(), List.of()));
        ConstraintViolation hardViolation = new ConstraintViolation("H1", Severity.HARD, "boom", null, LocalDate.now());
        when(scheduleValidator.validate(anyList(), anyList(), anyList(), any())).thenReturn(List.of(hardViolation));

        seeder.run();

        verify(scheduleRepository, never()).save(any());
        verify(shiftAssignmentRepository, never()).saveAll(anyList());
        verify(employeeRepository).saveAll(anyList());
    }

    @Test
    void doesNothingWhenVenueAlreadyExists() {
        DemoDataSeeder seeder = newSeeder();

        when(venueRepository.count()).thenReturn(1L);

        seeder.run();

        verify(venueRepository, never()).save(any());
        verify(shiftTemplateRepository, never()).save(any());
        verify(coverageRequirementRepository, never()).saveAll(anyList());
        verify(employeeRepository, never()).saveAll(anyList());
        verify(scheduleRepository, never()).save(any());
    }
}
