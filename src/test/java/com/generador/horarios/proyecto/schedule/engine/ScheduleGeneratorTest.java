package com.generador.horarios.proyecto.schedule.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.preference.Preference;
import com.generador.horarios.proyecto.preference.PreferenceType;
import com.generador.horarios.proyecto.schedule.Schedule;
import com.generador.horarios.proyecto.shift.ShiftAssignment;
import com.generador.horarios.proyecto.shift.ShiftSegment;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.venue.CoverageRequirement;
import com.generador.horarios.proyecto.venue.Venue;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScheduleGeneratorTest {

    // Lunes 2026-07-13 a domingo 2026-07-19
    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 13);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);

    private final ScheduleGenerator generator = new ScheduleGenerator(new ScheduleValidator());

    private Venue venue;
    private Schedule schedule;
    private ShiftTemplate manana;
    private ShiftTemplate tarde;

    @BeforeEach
    void setUp() {
        venue = new Venue("Bar Test", LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(venue, "id", 1L);

        schedule = new Schedule(venue, 2026, 29);

        manana = shiftTemplate("MAÑANA", new ShiftSegment(LocalTime.of(8, 0), LocalTime.of(16, 0)));
        tarde = shiftTemplate("TARDE", new ShiftSegment(LocalTime.of(16, 0), LocalTime.MIDNIGHT));
    }

    @Test
    void fillsAllSlotsWhenEnoughStaffAvailable() {
        Employee e1 = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee e2 = employee(20L, "Bea", ContractType.FULL_TIME, null);
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 2);

        GenerationResult result = generator.generate(schedule, List.of(e1, e2), List.of(requirement), List.of(), MONDAY);

        assertThat(result.assignments()).hasSize(2);
        assertThat(result.uncoveredSlots()).isEmpty();
    }

    @Test
    void registersUncoveredSlotWhenNotEnoughCandidates() {
        Employee e1 = employee(10L, "Ana", ContractType.FULL_TIME, null);
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 3);

        GenerationResult result = generator.generate(schedule, List.of(e1), List.of(requirement), List.of(), MONDAY);

        assertThat(result.assignments()).hasSize(1);
        assertThat(result.uncoveredSlots()).containsExactly(new UncoveredSlot(MONDAY, manana.getId(), 2));
    }

    @Test
    void respectsUnavailabilityWhenChoosingCandidates() {
        Employee unavailableEmployee = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee availableEmployee = employee(20L, "Bea", ContractType.FULL_TIME, null);
        Preference unavailable = new Preference(unavailableEmployee, PreferenceType.UNAVAILABLE, null, null, MONDAY, 0);
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);

        GenerationResult result = generator.generate(
                schedule, List.of(unavailableEmployee, availableEmployee), List.of(requirement), List.of(unavailable), MONDAY);

        assertThat(result.assignments()).hasSize(1);
        assertThat(result.assignments().get(0).getEmployee().getId()).isEqualTo(20L);
    }

    @Test
    void neverAssignsTheSameEmployeeBeyondDailyEffectiveHoursLimit() {
        // Un solo empleado; MAÑANA + TARDE el mismo día suman 16h > 9h (H4).
        // Debe cubrir solo uno de los dos huecos, nunca violar H4.
        Employee employee = employee(10L, "Ana", ContractType.FULL_TIME, null);
        CoverageRequirement morningRequirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);
        CoverageRequirement afternoonRequirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, tarde, 1);

        GenerationResult result = generator.generate(
                schedule, List.of(employee), List.of(morningRequirement, afternoonRequirement), List.of(), MONDAY);

        assertThat(result.assignments()).hasSize(1);
        assertThat(result.uncoveredSlots()).hasSize(1);
        assertThat(result.uncoveredSlots().get(0).missing()).isEqualTo(1);
    }

    @Test
    void prioritizesHarderToStaffSlotsFirstToMaximizeOverallCoverage() {
        // E1 (id más bajo, ganaría el desempate) es PART_TIME con solo 8h de
        // contrato: le alcanza para un único turno de 8h en toda la semana.
        // E2 es FULL_TIME pero no está disponible el martes.
        //
        // El martes (difícil: solo E1 cuenta como disponible) DEBE procesarse
        // antes que el lunes (fácil: E1 y E2 disponibles). Si se procesara en
        // el orden dado en la lista (lunes primero), el desempate por id más
        // bajo asignaría a E1 el lunes, agotando sus 8h, y el martes se
        // quedaría sin cubrir porque E2 no está disponible ese día.
        Employee tightBudgetEmployee = employee(10L, "Ana", ContractType.PART_TIME, 8);
        Employee unavailableTuesday = employee(20L, "Bea", ContractType.FULL_TIME, null);
        Preference unavailable =
                new Preference(unavailableTuesday, PreferenceType.UNAVAILABLE, null, null, TUESDAY, 0);

        CoverageRequirement mondayRequirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);
        CoverageRequirement tuesdayRequirement = new CoverageRequirement(venue, DayOfWeek.TUESDAY, manana, 1);

        GenerationResult result = generator.generate(
                schedule,
                List.of(tightBudgetEmployee, unavailableTuesday),
                List.of(mondayRequirement, tuesdayRequirement),
                List.of(unavailable),
                MONDAY);

        assertThat(result.uncoveredSlots()).isEmpty();
        assertThat(result.assignments()).hasSize(2);
        ShiftAssignment tuesdayAssignment = result.assignments().stream()
                .filter(a -> a.getDate().equals(TUESDAY))
                .findFirst()
                .orElseThrow();
        assertThat(tuesdayAssignment.getEmployee().getId()).isEqualTo(10L);
    }

    @Test
    void breaksTiesByLowestEmployeeIdWhenNoScoringExistsYet() {
        Employee lowerId = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee higherId = employee(20L, "Bea", ContractType.FULL_TIME, null);
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);

        GenerationResult result =
                generator.generate(schedule, List.of(higherId, lowerId), List.of(requirement), List.of(), MONDAY);

        assertThat(result.assignments().get(0).getEmployee().getId()).isEqualTo(10L);
    }

    private Employee employee(Long id, String name, ContractType contractType, Integer contractHours) {
        Employee employee = new Employee(name, name.toLowerCase() + "@test.com", contractType, contractHours, venue);
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }

    private ShiftTemplate shiftTemplate(String name, ShiftSegment... segments) {
        ShiftTemplate template = new ShiftTemplate(name, venue, List.of(segments));
        ReflectionTestUtils.setField(template, "id", (long) (name.hashCode() & 0xFFFF));
        return template;
    }
}
