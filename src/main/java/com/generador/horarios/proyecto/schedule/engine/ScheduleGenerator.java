package com.generador.horarios.proyecto.schedule.engine;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.preference.Preference;
import com.generador.horarios.proyecto.preference.PreferenceType;
import com.generador.horarios.proyecto.schedule.Schedule;
import com.generador.horarios.proyecto.shift.ShiftAssignment;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.venue.CoverageRequirement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Greedy sin equidad ni histórico (T2.4): cubre los huecos de
 * CoverageRequirement sin violar ninguna restricción dura, reutilizando
 * ScheduleValidator para decidir si un candidato es válido en cada hueco.
 * Sin puntuación todavía (S1-S3 llegan en T2.5-T2.7): entre varios
 * candidatos válidos desempata por employeeId ascendente, de forma
 * determinista. Java puro, no depende de Spring.
 */
public class ScheduleGenerator {

    private final ScheduleValidator scheduleValidator;

    public ScheduleGenerator(ScheduleValidator scheduleValidator) {
        this.scheduleValidator = scheduleValidator;
    }

    /**
     * @param employees            candidatos ya filtrados por el caller (activos, del venue)
     * @param coverageRequirements huecos a cubrir para esa semana/venue
     * @param weekStart            lunes de la semana ISO que se está generando
     */
    public GenerationResult generate(
            Schedule schedule,
            List<Employee> employees,
            List<CoverageRequirement> coverageRequirements,
            List<Preference> preferences,
            LocalDate weekStart) {

        Set<EmployeeDateKey> unavailable = preferences.stream()
                .filter(p -> p.getType() == PreferenceType.UNAVAILABLE)
                .map(p -> new EmployeeDateKey(p.getEmployee().getId(), p.getSpecificDate()))
                .collect(Collectors.toSet());

        List<CoverageSlotGroup> orderedGroups = orderByDifficulty(coverageRequirements, employees, unavailable, weekStart);

        List<ShiftAssignment> assignments = new ArrayList<>();
        List<UncoveredSlot> uncoveredSlots = new ArrayList<>();

        for (CoverageSlotGroup group : orderedGroups) {
            int filled = 0;
            for (int i = 0; i < group.requiredCount(); i++) {
                Employee chosen =
                        pickCandidate(group.date(), group.shiftTemplate(), employees, assignments, preferences, schedule, weekStart);
                if (chosen == null) {
                    break;
                }
                assignments.add(new ShiftAssignment(chosen, group.shiftTemplate(), schedule, group.date()));
                filled++;
            }
            if (filled < group.requiredCount()) {
                uncoveredSlots.add(new UncoveredSlot(group.date(), group.shiftTemplate().getId(), group.requiredCount() - filled));
            }
        }

        return new GenerationResult(assignments, uncoveredSlots);
    }

    /**
     * Paso 1 del algoritmo: procesar antes los huecos con menos candidatos
     * disponibles. Es una heurística barata (cuenta empleados activos que no
     * están UNAVAILABLE ese día) calculada una sola vez al principio; no se
     * recalcula a medida que se van haciendo asignaciones.
     */
    private List<CoverageSlotGroup> orderByDifficulty(
            List<CoverageRequirement> coverageRequirements, List<Employee> employees,
            Set<EmployeeDateKey> unavailable, LocalDate weekStart) {
        List<CoverageSlotGroup> groups = coverageRequirements.stream()
                .map(requirement -> {
                    LocalDate date = dateFor(weekStart, requirement.getDayOfWeek());
                    long availableCandidates = employees.stream()
                            .filter(Employee::isActive)
                            .filter(e -> !unavailable.contains(new EmployeeDateKey(e.getId(), date)))
                            .count();
                    return new CoverageSlotGroup(date, requirement.getShiftTemplate(), requirement.getRequiredCount(), availableCandidates);
                })
                .toList();

        return groups.stream()
                .sorted(Comparator.comparingLong(CoverageSlotGroup::availableCandidates)
                        .thenComparing(CoverageSlotGroup::date)
                        .thenComparing(g -> g.shiftTemplate().getId()))
                .toList();
    }

    private Employee pickCandidate(
            LocalDate date, ShiftTemplate shiftTemplate, List<Employee> employees, List<ShiftAssignment> currentAssignments,
            List<Preference> preferences, Schedule schedule, LocalDate weekStart) {
        return employees.stream()
                .filter(Employee::isActive)
                .sorted(Comparator.comparing(Employee::getId))
                .filter(employee -> canAssign(employee, shiftTemplate, date, currentAssignments, preferences, schedule, weekStart))
                .findFirst()
                .orElse(null);
    }

    /** Reutiliza ScheduleValidator en vez de duplicar H1-H6: prueba a añadir la
     *  asignación tentativa y comprueba que no aparezca ninguna HARD nueva para
     *  ese empleado. H7 no aplica aquí (no bloquea candidatos, es SOFT). */
    private boolean canAssign(
            Employee employee, ShiftTemplate shiftTemplate, LocalDate date, List<ShiftAssignment> currentAssignments,
            List<Preference> preferences, Schedule schedule, LocalDate weekStart) {
        List<ShiftAssignment> tentative = new ArrayList<>(currentAssignments);
        tentative.add(new ShiftAssignment(employee, shiftTemplate, schedule, date));

        return scheduleValidator.validate(tentative, preferences, List.of(), weekStart).stream()
                .noneMatch(violation -> violation.severity() == Severity.HARD && employee.getId().equals(violation.employeeId()));
    }

    private LocalDate dateFor(LocalDate weekStart, DayOfWeek dayOfWeek) {
        return weekStart.plusDays(dayOfWeek.getValue() - DayOfWeek.MONDAY.getValue());
    }

    private record EmployeeDateKey(Long employeeId, LocalDate date) {
    }

    private record CoverageSlotGroup(LocalDate date, ShiftTemplate shiftTemplate, int requiredCount, long availableCandidates) {
    }
}
