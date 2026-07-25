package com.generador.horarios.proyecto.schedule.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.Position;
import com.generador.horarios.proyecto.preference.Preference;
import com.generador.horarios.proyecto.preference.PreferenceType;
import com.generador.horarios.proyecto.shift.ShiftAssignment;
import com.generador.horarios.proyecto.shift.ShiftSegment;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.venue.CoverageRequirement;
import com.generador.horarios.proyecto.venue.Venue;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScheduleValidatorTest {

    // Lunes 2026-07-13 a domingo 2026-07-19
    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 13);

    private final ScheduleValidator validator = new ScheduleValidator();

    private Venue venue;
    private Employee fullTimeEmployee;
    private Employee partTimeEmployee;
    private ShiftTemplate manana;
    private ShiftTemplate tarde;
    private ShiftTemplate partido;

    @BeforeEach
    void setUp() {
        venue = new Venue("Bar Test", LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(venue, "id", 1L);

        fullTimeEmployee = new Employee("Ana", "ana@test.com", ContractType.FULL_TIME, null, venue);
        ReflectionTestUtils.setField(fullTimeEmployee, "id", 10L);

        partTimeEmployee = new Employee("Bea", "bea@test.com", ContractType.PART_TIME, 20, venue);
        ReflectionTestUtils.setField(partTimeEmployee, "id", 20L);

        manana = shiftTemplate("MAÑANA", new ShiftSegment(LocalTime.of(8, 0), LocalTime.of(16, 0)));
        tarde = shiftTemplate("TARDE", new ShiftSegment(LocalTime.of(16, 0), LocalTime.MIDNIGHT));
        partido = shiftTemplate("PARTIDO",
                new ShiftSegment(LocalTime.of(12, 0), LocalTime.of(16, 0)),
                new ShiftSegment(LocalTime.of(20, 0), LocalTime.MIDNIGHT));
    }

    @Test
    void noViolationsForACompliantWeek() {
        List<ShiftAssignment> assignments = List.of(
                assignment(fullTimeEmployee, manana, MONDAY),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(1)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(2)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(3)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(4)));

        List<ConstraintViolation> violations = validator.validate(assignments, List.of(), List.of(), MONDAY);

        assertThat(violations).isEmpty();
    }

    @Test
    void detectsLessThan12HoursRestBetweenShifts() {
        List<ShiftAssignment> assignments = List.of(
                assignment(fullTimeEmployee, tarde, MONDAY),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(1)));

        List<ConstraintViolation> violations = validator.validate(assignments, List.of(), List.of(), MONDAY);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.ruleId()).isEqualTo("H1");
            assertThat(v.severity()).isEqualTo(Severity.HARD);
            assertThat(v.employeeId()).isEqualTo(10L);
        });
    }

    @Test
    void detectsMissingWeeklyRestOf36Hours() {
        // Trabaja los 7 días de la semana: nunca hay un hueco de 36h.
        List<ShiftAssignment> assignments = List.of(
                assignment(fullTimeEmployee, manana, MONDAY),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(1)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(2)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(3)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(4)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(5)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(6)));

        List<ConstraintViolation> violations = validator.validate(assignments, List.of(), List.of(), MONDAY);

        assertThat(violations).anySatisfy(v -> assertThat(v.ruleId()).isEqualTo("H2"));
    }

    @Test
    void passesWithExactly36HourWeeklyRest() {
        // Lunes a viernes con MAÑANA (huecos de 16h entre días, por debajo de 36h) y
        // el sábado un turno corto que termina a mediodía: el hueco hasta el
        // siguiente lunes 00:00 es 1,5 días = 36h exactas, el único candidato a
        // igualar el límite. Debe pasar (36h no es "menos de" 36h).
        ShiftTemplate corto = shiftTemplate("CORTO", new ShiftSegment(LocalTime.of(8, 0), LocalTime.of(12, 0)));
        List<ShiftAssignment> assignments = List.of(
                assignment(fullTimeEmployee, manana, MONDAY),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(1)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(2)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(3)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(4)),
                assignment(fullTimeEmployee, corto, MONDAY.plusDays(5))); // sábado 08:00-12:00

        List<ConstraintViolation> violations = validator.validate(assignments, List.of(), List.of(), MONDAY);

        assertThat(violations).noneMatch(v -> v.ruleId().equals("H2"));
    }

    @Test
    void failsWhenWeeklyRestIsOneMinuteShortOf36Hours() {
        // Igual que el caso anterior pero el turno del sábado termina un minuto
        // más tarde (12:01): el hueco pasa a ser 35h59, por debajo del límite.
        ShiftTemplate cortoMasUnMinuto =
                shiftTemplate("CORTO_MAS_1", new ShiftSegment(LocalTime.of(8, 0), LocalTime.of(12, 1)));
        List<ShiftAssignment> assignments = List.of(
                assignment(fullTimeEmployee, manana, MONDAY),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(1)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(2)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(3)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(4)),
                assignment(fullTimeEmployee, cortoMasUnMinuto, MONDAY.plusDays(5)));

        List<ConstraintViolation> violations = validator.validate(assignments, List.of(), List.of(), MONDAY);

        assertThat(violations).anySatisfy(v -> assertThat(v.ruleId()).isEqualTo("H2"));
    }

    @Test
    void detectsExceedingMaxWeeklyHoursForFullTime() {
        List<ShiftAssignment> assignments = List.of(
                assignment(fullTimeEmployee, manana, MONDAY),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(1)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(2)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(3)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(4)),
                assignment(fullTimeEmployee, manana, MONDAY.plusDays(5))); // 6 días x 8h = 48h > 40h

        List<ConstraintViolation> violations = validator.validate(assignments, List.of(), List.of(), MONDAY);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.ruleId()).isEqualTo("H3");
            assertThat(v.employeeId()).isEqualTo(10L);
        });
    }

    @Test
    void respectsPartTimeContractHoursForH3() {
        // 20h de contrato; 3 turnos de mañana (8h) = 24h > 20h.
        List<ShiftAssignment> assignments = List.of(
                assignment(partTimeEmployee, manana, MONDAY),
                assignment(partTimeEmployee, manana, MONDAY.plusDays(1)),
                assignment(partTimeEmployee, manana, MONDAY.plusDays(2)));

        List<ConstraintViolation> violations = validator.validate(assignments, List.of(), List.of(), MONDAY);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.ruleId()).isEqualTo("H3");
            assertThat(v.employeeId()).isEqualTo(20L);
        });
    }

    @Test
    void detectsExceedingMaxDailyEffectiveHours() {
        ShiftTemplate largo = shiftTemplate("LARGO", new ShiftSegment(LocalTime.of(8, 0), LocalTime.of(18, 0))); // 10h
        List<ShiftAssignment> assignments = List.of(assignment(fullTimeEmployee, largo, MONDAY));

        List<ConstraintViolation> violations = validator.validate(assignments, List.of(), List.of(), MONDAY);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.ruleId()).isEqualTo("H4");
            assertThat(v.date()).isEqualTo(MONDAY);
        });
    }

    @Test
    void excludesUnpaidBreakFromEffectiveHoursForSplitShifts() {
        // PARTIDO: 12-16 + 20-00 = 8h efectivas, aunque la jornada abarca 12h (12:00-00:00).
        List<ShiftAssignment> assignments = List.of(assignment(fullTimeEmployee, partido, MONDAY));

        List<ConstraintViolation> violations = validator.validate(assignments, List.of(), List.of(), MONDAY);

        assertThat(violations).noneMatch(v -> v.ruleId().equals("H4"));
    }

    @Test
    void detectsAssignmentOnUnavailableDate() {
        Preference unavailable = new Preference(
                fullTimeEmployee, PreferenceType.UNAVAILABLE, null, null, MONDAY, 0);
        List<ShiftAssignment> assignments = List.of(assignment(fullTimeEmployee, manana, MONDAY));

        List<ConstraintViolation> violations =
                validator.validate(assignments, List.of(unavailable), List.of(), MONDAY);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.ruleId()).isEqualTo("H5");
            assertThat(v.employeeId()).isEqualTo(10L);
            assertThat(v.date()).isEqualTo(MONDAY);
        });
    }

    @Test
    void allowsAssignmentWhenNoUnavailabilityMatches() {
        Preference unavailable = new Preference(
                fullTimeEmployee, PreferenceType.UNAVAILABLE, null, null, MONDAY.plusDays(2), 0);
        List<ShiftAssignment> assignments = List.of(assignment(fullTimeEmployee, manana, MONDAY));

        List<ConstraintViolation> violations =
                validator.validate(assignments, List.of(unavailable), List.of(), MONDAY);

        assertThat(violations).noneMatch(v -> v.ruleId().equals("H5"));
    }

    @Test
    void detectsOverlappingShiftsForSameEmployee() {
        ShiftTemplate mediodia = shiftTemplate("MEDIODIA", new ShiftSegment(LocalTime.of(12, 0), LocalTime.of(20, 0)));
        List<ShiftAssignment> assignments = List.of(
                assignment(fullTimeEmployee, manana, MONDAY),   // 08:00-16:00
                assignment(fullTimeEmployee, mediodia, MONDAY)); // 12:00-20:00 -> solapa 12:00-16:00

        List<ConstraintViolation> violations = validator.validate(assignments, List.of(), List.of(), MONDAY);

        assertThat(violations).anySatisfy(v -> assertThat(v.ruleId()).isEqualTo("H6"));
    }

    @Test
    void allowsBackToBackShiftsThatOnlyTouch() {
        // MAÑANA termina 16:00, TARDE empieza 16:00: se tocan, no se solapan.
        List<ShiftAssignment> assignments = List.of(
                assignment(fullTimeEmployee, manana, MONDAY),
                assignment(fullTimeEmployee, tarde, MONDAY));

        List<ConstraintViolation> violations = validator.validate(assignments, List.of(), List.of(), MONDAY);

        assertThat(violations).noneMatch(v -> v.ruleId().equals("H6"));
    }

    @Test
    void flagsInsufficientCoverageAsSoftNotHard() {
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, tarde, 3);
        List<ShiftAssignment> assignments = List.of(assignment(fullTimeEmployee, tarde, MONDAY)); // solo 1, hacen falta 3

        List<ConstraintViolation> violations =
                validator.validate(assignments, List.of(), List.of(requirement), MONDAY);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.ruleId()).isEqualTo("H7");
            assertThat(v.severity()).isEqualTo(Severity.SOFT);
        });
    }

    @Test
    void doesNotFlagCoverageWhenRequirementIsMet() {
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, tarde, 1);
        List<ShiftAssignment> assignments = List.of(assignment(fullTimeEmployee, tarde, MONDAY));

        List<ConstraintViolation> violations =
                validator.validate(assignments, List.of(), List.of(requirement), MONDAY);

        assertThat(violations).noneMatch(v -> v.ruleId().equals("H7"));
    }

    @Test
    void flagsInsufficientCoverageForARequiredPositionEvenWhenHeadcountIsMet() {
        Employee waiter = new Employee("Bea", "bea@test.com", ContractType.FULL_TIME, null, venue);
        waiter.setPositions(Set.of(Position.CAMARERO));
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, tarde, 1);
        requirement.setPosition(Position.COCINERO);
        // fullTimeEmployee no tiene puesto asignado, así que no cuenta como cocinero aunque cubra el hueco en número.
        List<ShiftAssignment> assignments = List.of(assignment(fullTimeEmployee, tarde, MONDAY));

        List<ConstraintViolation> violations =
                validator.validate(assignments, List.of(), List.of(requirement), MONDAY);

        assertThat(violations).anySatisfy(v -> {
            assertThat(v.ruleId()).isEqualTo("H7");
            assertThat(v.severity()).isEqualTo(Severity.SOFT);
        });
    }

    @Test
    void doesNotFlagCoverageWhenTheAssignedEmployeeHasTheRequiredPosition() {
        fullTimeEmployee.setPositions(Set.of(Position.COCINERO));
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, tarde, 1);
        requirement.setPosition(Position.COCINERO);
        List<ShiftAssignment> assignments = List.of(assignment(fullTimeEmployee, tarde, MONDAY));

        List<ConstraintViolation> violations =
                validator.validate(assignments, List.of(), List.of(requirement), MONDAY);

        assertThat(violations).noneMatch(v -> v.ruleId().equals("H7"));
    }

    private ShiftTemplate shiftTemplate(String name, ShiftSegment... segments) {
        ShiftTemplate template = new ShiftTemplate(name, venue, List.of(segments));
        ReflectionTestUtils.setField(template, "id", (long) (name.hashCode() & 0xFFFF));
        return template;
    }

    private ShiftAssignment assignment(Employee employee, ShiftTemplate shiftTemplate, LocalDate date) {
        return new ShiftAssignment(employee, shiftTemplate, null, date);
    }
}
