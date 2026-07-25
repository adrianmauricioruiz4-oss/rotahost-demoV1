package com.generador.horarios.proyecto.schedule.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.Position;
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
import java.util.Set;
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

        GenerationResult result = generate(List.of(e1, e2), List.of(requirement), List.of());

        assertThat(result.assignments()).hasSize(2);
        assertThat(result.uncoveredSlots()).isEmpty();
    }

    @Test
    void registersUncoveredSlotWhenNotEnoughCandidates() {
        Employee e1 = employee(10L, "Ana", ContractType.FULL_TIME, null);
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 3);

        GenerationResult result = generate(List.of(e1), List.of(requirement), List.of());

        assertThat(result.assignments()).hasSize(1);
        assertThat(result.uncoveredSlots()).containsExactly(new UncoveredSlot(MONDAY, manana.getId(), 2));
    }

    @Test
    void onlyAssignsCandidatesWithTheRequiredPosition() {
        Employee cook = employee(10L, "Ana", ContractType.FULL_TIME, null);
        cook.setPositions(Set.of(Position.COCINERO));
        Employee waiter = employee(20L, "Bea", ContractType.FULL_TIME, null);
        waiter.setPositions(Set.of(Position.CAMARERO));
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);
        requirement.setPosition(Position.CAMARERO);

        GenerationResult result = generate(List.of(cook, waiter), List.of(requirement), List.of());

        assertThat(result.assignments()).hasSize(1);
        assertThat(result.assignments().get(0).getEmployee().getId()).isEqualTo(20L);
    }

    @Test
    void leavesSlotUncoveredWhenNoOneHasTheRequiredPositionEvenWithEnoughHeadcount() {
        Employee cookOne = employee(10L, "Ana", ContractType.FULL_TIME, null);
        cookOne.setPositions(Set.of(Position.COCINERO));
        Employee cookTwo = employee(20L, "Bea", ContractType.FULL_TIME, null);
        cookTwo.setPositions(Set.of(Position.COCINERO));
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);
        requirement.setPosition(Position.CAMARERO);

        GenerationResult result = generate(List.of(cookOne, cookTwo), List.of(requirement), List.of());

        assertThat(result.assignments()).isEmpty();
        assertThat(result.uncoveredSlots()).containsExactly(new UncoveredSlot(MONDAY, manana.getId(), 1));
    }

    @Test
    void respectsUnavailabilityWhenChoosingCandidates() {
        Employee unavailableEmployee = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee availableEmployee = employee(20L, "Bea", ContractType.FULL_TIME, null);
        Preference unavailable = new Preference(unavailableEmployee, PreferenceType.UNAVAILABLE, null, null, MONDAY, 0);
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);

        GenerationResult result =
                generate(List.of(unavailableEmployee, availableEmployee), List.of(requirement), List.of(unavailable));

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

        GenerationResult result = generate(List.of(employee), List.of(morningRequirement, afternoonRequirement), List.of());

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

        GenerationResult result = generate(
                List.of(tightBudgetEmployee, unavailableTuesday),
                List.of(mondayRequirement, tuesdayRequirement),
                List.of(unavailable));

        assertThat(result.uncoveredSlots()).isEmpty();
        assertThat(result.assignments()).hasSize(2);
        ShiftAssignment tuesdayAssignment = result.assignments().stream()
                .filter(a -> a.getDate().equals(TUESDAY))
                .findFirst()
                .orElseThrow();
        assertThat(tuesdayAssignment.getEmployee().getId()).isEqualTo(10L);
    }

    @Test
    void breaksTiesByLowestEmployeeIdWhenScoresAreEqual() {
        Employee lowerId = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee higherId = employee(20L, "Bea", ContractType.FULL_TIME, null);
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);

        GenerationResult result = generate(List.of(higherId, lowerId), List.of(requirement), List.of());

        assertThat(result.assignments().get(0).getEmployee().getId()).isEqualTo(10L);
    }

    @Test
    void prefersCandidateWithHigherDayPreferenceScoreOverLowerEmployeeId() {
        // Bea tiene id más alto (perdería el desempate), pero prefiere trabajar
        // los lunes con peso 5: debe ganarle a Ana, que no tiene preferencias.
        Employee ana = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee bea = employee(20L, "Bea", ContractType.FULL_TIME, null);
        Preference beaPrefersMonday = new Preference(bea, PreferenceType.PREFERS_DAY, DayOfWeek.MONDAY, null, null, 5);
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);

        GenerationResult result = generate(List.of(ana, bea), List.of(requirement), List.of(beaPrefersMonday));

        assertThat(result.assignments().get(0).getEmployee().getId()).isEqualTo(20L);
    }

    @Test
    void prefersCandidateWithHigherShiftPreferenceScoreOverLowerEmployeeId() {
        Employee ana = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee bea = employee(20L, "Bea", ContractType.FULL_TIME, null);
        Preference beaPrefersTarde = new Preference(bea, PreferenceType.PREFERS_SHIFT, null, tarde, null, 3);
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, tarde, 1);

        GenerationResult result = generate(List.of(ana, bea), List.of(requirement), List.of(beaPrefersTarde));

        assertThat(result.assignments().get(0).getEmployee().getId()).isEqualTo(20L);
    }

    @Test
    void avoidsPenalizesCandidateBelowANeutralOne() {
        // Ana (id más bajo, ganaría el desempate) evita los lunes con peso 4:
        // su puntuación queda en -4, por debajo de Bea (neutral, puntuación 0).
        Employee ana = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee bea = employee(20L, "Bea", ContractType.FULL_TIME, null);
        Preference anaAvoidsMonday = new Preference(ana, PreferenceType.AVOIDS_DAY, DayOfWeek.MONDAY, null, null, 4);
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);

        GenerationResult result = generate(List.of(ana, bea), List.of(requirement), List.of(anaAvoidsMonday));

        assertThat(result.assignments().get(0).getEmployee().getId()).isEqualTo(20L);
    }

    @Test
    void sumsMultipleMatchingPreferencesForTheSameCandidate() {
        // Ana acumula PREFERS_DAY(lunes, 2) + PREFERS_SHIFT(mañana, 2) = 4,
        // más que la preferencia única de Bea (PREFERS_DAY lunes, 3).
        Employee ana = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee bea = employee(20L, "Bea", ContractType.FULL_TIME, null);
        Preference anaPrefersMonday = new Preference(ana, PreferenceType.PREFERS_DAY, DayOfWeek.MONDAY, null, null, 2);
        Preference anaPrefersManana = new Preference(ana, PreferenceType.PREFERS_SHIFT, null, manana, null, 2);
        Preference beaPrefersMonday = new Preference(bea, PreferenceType.PREFERS_DAY, DayOfWeek.MONDAY, null, null, 3);
        CoverageRequirement requirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);

        GenerationResult result = generate(
                List.of(ana, bea), List.of(requirement),
                List.of(anaPrefersMonday, anaPrefersManana, beaPrefersMonday));

        assertThat(result.assignments().get(0).getEmployee().getId()).isEqualTo(10L);
    }

    @Test
    void penalizesBadShiftCandidateWithMoreAccumulatedBadShiftsThisWeek() {
        // Viernes y sábado noche (TARDE) son ambos turnos malos, con descanso de
        // sobra entre ellos (16h). Sin preferencias, el primero (viernes, procesado
        // antes por fecha) lo gana Ana por desempate de id; eso le suma un turno
        // malo, así que el segundo (sábado) debe irse a Bea por equidad.
        Employee ana = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee bea = employee(20L, "Bea", ContractType.FULL_TIME, null);
        LocalDate friday = MONDAY.plusDays(4);
        LocalDate saturday = MONDAY.plusDays(5);
        CoverageRequirement fridayRequirement = new CoverageRequirement(venue, DayOfWeek.FRIDAY, tarde, 1);
        CoverageRequirement saturdayRequirement = new CoverageRequirement(venue, DayOfWeek.SATURDAY, tarde, 1);

        GenerationResult result = generate(List.of(ana, bea), List.of(fridayRequirement, saturdayRequirement), List.of());

        assertThat(result.assignments()).hasSize(2);
        ShiftAssignment fridayAssignment =
                result.assignments().stream().filter(a -> a.getDate().equals(friday)).findFirst().orElseThrow();
        ShiftAssignment saturdayAssignment =
                result.assignments().stream().filter(a -> a.getDate().equals(saturday)).findFirst().orElseThrow();
        assertThat(fridayAssignment.getEmployee().getId()).isEqualTo(10L);
        assertThat(saturdayAssignment.getEmployee().getId()).isEqualTo(20L);
    }

    @Test
    void penalizesBadShiftCandidateWithHistoricalBadShifts() {
        // Ana ya acumula 2 turnos malos en las 3 semanas anteriores; Bea, ninguno.
        // Para un hueco de domingo, debe preferir a Bea aunque Ana tenga id más bajo.
        Employee ana = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee bea = employee(20L, "Bea", ContractType.FULL_TIME, null);
        LocalDate threeWeeksAgo = MONDAY.minusWeeks(2);
        ShiftAssignment historicalSunday1 =
                new ShiftAssignment(ana, tarde, schedule, threeWeeksAgo.plusDays(6));
        ShiftAssignment historicalSunday2 =
                new ShiftAssignment(ana, tarde, schedule, threeWeeksAgo.plusWeeks(1).plusDays(6));
        CoverageRequirement sundayRequirement = new CoverageRequirement(venue, DayOfWeek.SUNDAY, manana, 1);

        GenerationResult result = generator.generate(
                schedule, List.of(ana, bea), List.of(sundayRequirement), List.of(),
                List.of(historicalSunday1, historicalSunday2), MONDAY);

        assertThat(result.assignments().get(0).getEmployee().getId()).isEqualTo(20L);
    }

    @Test
    void doesNotApplyEquityPenaltyToRegularShifts() {
        // Turno de martes por la mañana: no es un turno malo, así que el histórico
        // de domingos de Ana no debería penalizarla frente a Bea.
        Employee ana = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee bea = employee(20L, "Bea", ContractType.FULL_TIME, null);
        LocalDate threeWeeksAgo = MONDAY.minusWeeks(3);
        ShiftAssignment historicalSunday = new ShiftAssignment(ana, tarde, schedule, threeWeeksAgo.plusDays(6));
        CoverageRequirement tuesdayRequirement = new CoverageRequirement(venue, DayOfWeek.TUESDAY, manana, 1);

        GenerationResult result = generator.generate(
                schedule, List.of(ana, bea), List.of(tuesdayRequirement), List.of(),
                List.of(historicalSunday), MONDAY);

        // Sin penalización de equidad, el desempate vuelve a ser por id más bajo.
        assertThat(result.assignments().get(0).getEmployee().getId()).isEqualTo(10L);
    }

    @Test
    void equityReportCountsBadShiftsThisWeekAndWithHistory() {
        Employee ana = employee(10L, "Ana", ContractType.FULL_TIME, null);
        LocalDate threeWeeksAgo = MONDAY.minusWeeks(2);
        ShiftAssignment historicalSunday = new ShiftAssignment(ana, tarde, schedule, threeWeeksAgo.plusDays(6));
        CoverageRequirement sundayRequirement = new CoverageRequirement(venue, DayOfWeek.SUNDAY, manana, 1);

        GenerationResult result = generator.generate(
                schedule, List.of(ana), List.of(sundayRequirement), List.of(),
                List.of(historicalSunday), MONDAY);

        assertThat(result.equityReport()).containsExactly(new EquityReportEntry(10L, 1, 2));
    }

    @Test
    void penalizesAbruptRotationFromLateShiftToEarlierStartingShiftNextDayEvenWhenH1IsSatisfied() {
        // TARDE (empieza 16:00) el lunes, MEDIODIA (empieza 12:00, más temprano)
        // el martes: el descanso es exactamente 12h (cumple H1), pero es rotación
        // hacia atrás. Ana gana el lunes por desempate; para el martes debe perder
        // frente a Bea, que no tiene esa rotación brusca.
        ShiftTemplate mediodia = shiftTemplate("MEDIODIA", new ShiftSegment(LocalTime.of(12, 0), LocalTime.of(20, 0)));
        Employee ana = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee bea = employee(20L, "Bea", ContractType.FULL_TIME, null);
        CoverageRequirement mondayTarde = new CoverageRequirement(venue, DayOfWeek.MONDAY, tarde, 1);
        CoverageRequirement tuesdayMediodia = new CoverageRequirement(venue, DayOfWeek.TUESDAY, mediodia, 1);

        GenerationResult result = generate(List.of(ana, bea), List.of(mondayTarde, tuesdayMediodia), List.of());

        assertThat(result.assignments()).hasSize(2);
        ShiftAssignment mondayAssignment =
                result.assignments().stream().filter(a -> a.getDate().equals(MONDAY)).findFirst().orElseThrow();
        ShiftAssignment tuesdayAssignment =
                result.assignments().stream().filter(a -> a.getDate().equals(TUESDAY)).findFirst().orElseThrow();
        assertThat(mondayAssignment.getEmployee().getId()).isEqualTo(10L);
        assertThat(tuesdayAssignment.getEmployee().getId()).isEqualTo(20L);
    }

    @Test
    void penalizesCreatingAnIsolatedSingleDayOff() {
        // Ana gana el lunes por desempate. Para el miércoles, asignárselo también
        // a Ana dejaría el martes como día libre suelto entre dos días trabajados;
        // debe preferir a Bea, que no tiene esa fragmentación.
        LocalDate wednesday = MONDAY.plusDays(2);
        Employee ana = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee bea = employee(20L, "Bea", ContractType.FULL_TIME, null);
        CoverageRequirement mondayRequirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);
        CoverageRequirement wednesdayRequirement = new CoverageRequirement(venue, DayOfWeek.WEDNESDAY, manana, 1);

        GenerationResult result = generate(List.of(ana, bea), List.of(mondayRequirement, wednesdayRequirement), List.of());

        assertThat(result.assignments()).hasSize(2);
        ShiftAssignment mondayAssignment =
                result.assignments().stream().filter(a -> a.getDate().equals(MONDAY)).findFirst().orElseThrow();
        ShiftAssignment wednesdayAssignment =
                result.assignments().stream().filter(a -> a.getDate().equals(wednesday)).findFirst().orElseThrow();
        assertThat(mondayAssignment.getEmployee().getId()).isEqualTo(10L);
        assertThat(wednesdayAssignment.getEmployee().getId()).isEqualTo(20L);
    }

    @Test
    void doesNotPenalizeConsecutiveWorkDays() {
        // Ana trabaja lunes y martes seguidos (racha sin huecos); para el
        // miércoles no hay ningún día suelto que fragmentar, así que no debería
        // haber penalización de S4 y el desempate vuelve a ser por id más bajo.
        LocalDate wednesday = MONDAY.plusDays(2);
        Employee ana = employee(10L, "Ana", ContractType.FULL_TIME, null);
        Employee bea = employee(20L, "Bea", ContractType.FULL_TIME, null);
        CoverageRequirement mondayRequirement = new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1);
        CoverageRequirement tuesdayRequirement = new CoverageRequirement(venue, DayOfWeek.TUESDAY, manana, 1);
        CoverageRequirement wednesdayRequirement = new CoverageRequirement(venue, DayOfWeek.WEDNESDAY, manana, 1);

        GenerationResult result = generate(
                List.of(ana, bea), List.of(mondayRequirement, tuesdayRequirement, wednesdayRequirement), List.of());

        assertThat(result.assignments()).hasSize(3);
        ShiftAssignment wednesdayAssignment =
                result.assignments().stream().filter(a -> a.getDate().equals(wednesday)).findFirst().orElseThrow();
        assertThat(wednesdayAssignment.getEmployee().getId()).isEqualTo(10L);
    }

    private GenerationResult generate(List<Employee> employees, List<CoverageRequirement> requirements, List<Preference> preferences) {
        return generator.generate(schedule, employees, requirements, preferences, List.of(), MONDAY);
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
