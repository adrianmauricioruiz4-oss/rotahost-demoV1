package com.generador.horarios.proyecto.schedule.engine;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.Position;
import com.generador.horarios.proyecto.preference.Preference;
import com.generador.horarios.proyecto.preference.PreferenceType;
import com.generador.horarios.proyecto.schedule.Schedule;
import com.generador.horarios.proyecto.shift.ShiftAssignment;
import com.generador.horarios.proyecto.shift.ShiftSegment;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.venue.CoverageRequirement;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Greedy (T2.4) con puntuación de preferencias blandas S1 (T2.5), equidad de
 * turnos malos S2 (T2.6) y suavizado de rotación S3 + agrupación de
 * libranzas S4 (T2.7): cubre los huecos de CoverageRequirement sin violar
 * ninguna restricción dura, reutilizando ScheduleValidator para decidir si
 * un candidato es válido en cada hueco, y entre los válidos elige el de
 * mayor puntuación combinada. En empate de puntuación desempata por
 * employeeId ascendente, de forma determinista. Java puro, no depende de
 * Spring.
 */
public class ScheduleGenerator {

    /** Peso de la penalización de equidad por cada turno malo ya acumulado. */
    private static final int BAD_SHIFT_EQUITY_PENALTY = 3;

    /** Peso de la penalización por rotación brusca (S3) e islas de un solo día libre (S4). */
    private static final int ROTATION_PENALTY = 2;
    private static final int ISOLATED_DAY_OFF_PENALTY = 2;

    private final ScheduleValidator scheduleValidator;

    public ScheduleGenerator(ScheduleValidator scheduleValidator) {
        this.scheduleValidator = scheduleValidator;
    }

    /**
     * @param employees             candidatos ya filtrados por el caller (activos, del venue)
     * @param coverageRequirements  huecos a cubrir para esa semana/venue
     * @param historicalAssignments asignaciones de las 3 semanas anteriores (ya
     *                               filtradas por el caller), usadas solo para
     *                               contar turnos malos acumulados (S2)
     * @param weekStart              lunes de la semana ISO que se está generando
     */
    public GenerationResult generate(
            Schedule schedule,
            List<Employee> employees,
            List<CoverageRequirement> coverageRequirements,
            List<Preference> preferences,
            List<ShiftAssignment> historicalAssignments,
            LocalDate weekStart) {

        Set<EmployeeDateKey> unavailable = preferences.stream()
                .filter(p -> p.getType() == PreferenceType.UNAVAILABLE)
                .map(p -> new EmployeeDateKey(p.getEmployee().getId(), p.getSpecificDate()))
                .collect(Collectors.toSet());
        Map<Long, List<Preference>> preferencesByEmployee = preferences.stream()
                .collect(Collectors.groupingBy(p -> p.getEmployee().getId()));
        Map<Long, Integer> historicalBadShiftCounts = historicalAssignments.stream()
                .filter(a -> isBadShift(a.getDate(), a.getShiftTemplate()))
                .collect(Collectors.groupingBy(a -> a.getEmployee().getId(), Collectors.summingInt(a -> 1)));
        Map<Long, Integer> currentBadShiftCounts = new HashMap<>();

        List<CoverageSlotGroup> orderedGroups = orderByDifficulty(coverageRequirements, employees, unavailable, weekStart);

        List<ShiftAssignment> assignments = new ArrayList<>();
        List<UncoveredSlot> uncoveredSlots = new ArrayList<>();

        for (CoverageSlotGroup group : orderedGroups) {
            int filled = 0;
            for (int i = 0; i < group.requiredCount(); i++) {
                Employee chosen = pickCandidate(group.date(), group.shiftTemplate(), group.position(), employees, assignments,
                        preferences, preferencesByEmployee, historicalBadShiftCounts, currentBadShiftCounts, schedule, weekStart);
                if (chosen == null) {
                    break;
                }
                assignments.add(new ShiftAssignment(chosen, group.shiftTemplate(), schedule, group.date()));
                filled++;
                if (isBadShift(group.date(), group.shiftTemplate())) {
                    currentBadShiftCounts.merge(chosen.getId(), 1, Integer::sum);
                }
            }
            if (filled < group.requiredCount()) {
                uncoveredSlots.add(new UncoveredSlot(group.date(), group.shiftTemplate().getId(), group.requiredCount() - filled));
            }
        }

        List<EquityReportEntry> equityReport = employees.stream()
                .map(employee -> {
                    int thisWeek = currentBadShiftCounts.getOrDefault(employee.getId(), 0);
                    int historical = historicalBadShiftCounts.getOrDefault(employee.getId(), 0);
                    return new EquityReportEntry(employee.getId(), thisWeek, thisWeek + historical);
                })
                .sorted(Comparator.comparing(EquityReportEntry::employeeId))
                .toList();

        return new GenerationResult(assignments, uncoveredSlots, equityReport);
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
                            .filter(e -> hasRequiredPosition(e, requirement.getPosition()))
                            .count();
                    return new CoverageSlotGroup(
                            date, requirement.getShiftTemplate(), requirement.getPosition(), requirement.getRequiredCount(), availableCandidates);
                })
                .toList();

        return groups.stream()
                .sorted(Comparator.comparingLong(CoverageSlotGroup::availableCandidates)
                        .thenComparing(CoverageSlotGroup::date)
                        .thenComparing(g -> g.shiftTemplate().getId()))
                .toList();
    }

    /**
     * Entre los candidatos que no violan ninguna dura, elige el de mayor
     * puntuación combinada (S1 preferencias + S2 equidad + S3 rotación + S4
     * libranzas agrupadas). En empate desempata por employeeId ascendente.
     */
    private Employee pickCandidate(
            LocalDate date, ShiftTemplate shiftTemplate, Position position, List<Employee> employees, List<ShiftAssignment> currentAssignments,
            List<Preference> preferences, Map<Long, List<Preference>> preferencesByEmployee,
            Map<Long, Integer> historicalBadShiftCounts, Map<Long, Integer> currentBadShiftCounts,
            Schedule schedule, LocalDate weekStart) {
        return employees.stream()
                .filter(Employee::isActive)
                .filter(employee -> hasRequiredPosition(employee, position))
                .filter(employee -> canAssign(employee, shiftTemplate, date, currentAssignments, preferences, schedule, weekStart))
                .max(Comparator
                        .<Employee>comparingInt(employee -> preferenceScore(employee, date, shiftTemplate, preferencesByEmployee)
                                + equityPenalty(employee, date, shiftTemplate, historicalBadShiftCounts, currentBadShiftCounts)
                                + rotationPenalty(employee, date, shiftTemplate, currentAssignments)
                                + isolatedDayOffPenalty(employee, date, currentAssignments))
                        .thenComparing(Comparator.comparing(Employee::getId).reversed()))
                .orElse(null);
    }

    /** + peso de PREFERS_DAY/PREFERS_SHIFT que aplican a este hueco, - peso de AVOIDS_DAY/AVOIDS_SHIFT. */
    private int preferenceScore(
            Employee employee, LocalDate date, ShiftTemplate shiftTemplate, Map<Long, List<Preference>> preferencesByEmployee) {
        int score = 0;
        for (Preference preference : preferencesByEmployee.getOrDefault(employee.getId(), List.of())) {
            switch (preference.getType()) {
                case PREFERS_DAY -> {
                    if (preference.getDayOfWeek() == date.getDayOfWeek()) {
                        score += preference.getWeight();
                    }
                }
                case AVOIDS_DAY -> {
                    if (preference.getDayOfWeek() == date.getDayOfWeek()) {
                        score -= preference.getWeight();
                    }
                }
                case PREFERS_SHIFT -> {
                    if (preference.getShiftTemplate() != null && preference.getShiftTemplate().getId().equals(shiftTemplate.getId())) {
                        score += preference.getWeight();
                    }
                }
                case AVOIDS_SHIFT -> {
                    if (preference.getShiftTemplate() != null && preference.getShiftTemplate().getId().equals(shiftTemplate.getId())) {
                        score -= preference.getWeight();
                    }
                }
                case UNAVAILABLE -> {
                    // restricción dura (H5), no puntúa
                }
            }
        }
        return score;
    }

    /**
     * S2: solo penaliza cuando el hueco en sí es un turno malo. Cuanto más
     * turnos malos lleve ya el candidato (histórico de 3 semanas + los ya
     * asignados esta misma semana), más se penaliza, para repartirlos.
     */
    private int equityPenalty(
            Employee employee, LocalDate date, ShiftTemplate shiftTemplate,
            Map<Long, Integer> historicalBadShiftCounts, Map<Long, Integer> currentBadShiftCounts) {
        if (!isBadShift(date, shiftTemplate)) {
            return 0;
        }
        int accumulated = historicalBadShiftCounts.getOrDefault(employee.getId(), 0)
                + currentBadShiftCounts.getOrDefault(employee.getId(), 0);
        return -accumulated * BAD_SHIFT_EQUITY_PENALTY;
    }

    /**
     * S2: domingo (cualquier turno) o viernes/sábado de noche. No hay
     * concepto de festivo todavía (backlog: "Gestión de festivos por CCAA").
     */
    private boolean isBadShift(LocalDate date, ShiftTemplate shiftTemplate) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return true;
        }
        return (dayOfWeek == DayOfWeek.FRIDAY || dayOfWeek == DayOfWeek.SATURDAY) && isNightShift(shiftTemplate);
    }

    /** Propiedad del turno, no de la fecha: cruza medianoche o termina a las 20:00 o más tarde. */
    private boolean isNightShift(ShiftTemplate shiftTemplate) {
        List<ShiftSegment> segments = shiftTemplate.getSegments();
        LocalTime lastSegmentEnd = segments.get(segments.size() - 1).getEndTime();
        return lastSegmentEnd.equals(LocalTime.MIDNIGHT) || !lastSegmentEnd.isBefore(LocalTime.of(20, 0));
    }

    /**
     * S3: penaliza aunque H1 (12h) ya esté satisfecho — "tarde -> mañana al
     * día siguiente" es brusco aunque cumpla el descanso mínimo. Se considera
     * brusco cuando la hora de inicio de un día es más temprana que la del
     * día anterior. Comprueba ambas direcciones (día-1 y día+1) porque el
     * relleno no es estrictamente cronológico (va por dificultad).
     */
    private int rotationPenalty(
            Employee employee, LocalDate date, ShiftTemplate shiftTemplate, List<ShiftAssignment> currentAssignments) {
        LocalTime thisStart = firstSegmentStart(shiftTemplate);
        int penalty = 0;

        Optional<ShiftAssignment> previousDay = findAssignment(employee, date.minusDays(1), currentAssignments);
        if (previousDay.isPresent() && thisStart.isBefore(firstSegmentStart(previousDay.get().getShiftTemplate()))) {
            penalty -= ROTATION_PENALTY;
        }

        Optional<ShiftAssignment> nextDay = findAssignment(employee, date.plusDays(1), currentAssignments);
        if (nextDay.isPresent() && firstSegmentStart(nextDay.get().getShiftTemplate()).isBefore(thisStart)) {
            penalty -= ROTATION_PENALTY;
        }

        return penalty;
    }

    /**
     * S4: sin visión de la semana completa ni backtracking, penaliza el caso
     * local detectable: asignar este hueco dejaría un único día libre
     * aislado entre dos días trabajados (p. ej. trabaja lunes, nada el
     * martes, y le asignamos miércoles -> el martes queda suelto).
     */
    private int isolatedDayOffPenalty(Employee employee, LocalDate date, List<ShiftAssignment> currentAssignments) {
        int penalty = 0;
        boolean workedTwoDaysBefore = findAssignment(employee, date.minusDays(2), currentAssignments).isPresent();
        boolean workedDayBefore = findAssignment(employee, date.minusDays(1), currentAssignments).isPresent();
        if (workedTwoDaysBefore && !workedDayBefore) {
            penalty -= ISOLATED_DAY_OFF_PENALTY;
        }

        boolean workedTwoDaysAfter = findAssignment(employee, date.plusDays(2), currentAssignments).isPresent();
        boolean workedDayAfter = findAssignment(employee, date.plusDays(1), currentAssignments).isPresent();
        if (workedTwoDaysAfter && !workedDayAfter) {
            penalty -= ISOLATED_DAY_OFF_PENALTY;
        }

        return penalty;
    }

    private Optional<ShiftAssignment> findAssignment(Employee employee, LocalDate date, List<ShiftAssignment> assignments) {
        return assignments.stream()
                .filter(a -> a.getEmployee().getId().equals(employee.getId()) && a.getDate().equals(date))
                .findFirst();
    }

    private LocalTime firstSegmentStart(ShiftTemplate shiftTemplate) {
        return shiftTemplate.getSegments().get(0).getStartTime();
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

    /** null = hueco sin puesto exigido (T5.3): cualquier empleado activo es candidato, como antes. */
    private boolean hasRequiredPosition(Employee employee, Position position) {
        return position == null || employee.getPositions().contains(position);
    }

    private record EmployeeDateKey(Long employeeId, LocalDate date) {
    }

    private record CoverageSlotGroup(
            LocalDate date, ShiftTemplate shiftTemplate, Position position, int requiredCount, long availableCandidates) {
    }
}
