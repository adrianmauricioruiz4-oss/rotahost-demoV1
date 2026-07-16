package com.generador.horarios.proyecto.schedule.engine;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.preference.Preference;
import com.generador.horarios.proyecto.preference.PreferenceType;
import com.generador.horarios.proyecto.shift.ShiftAssignment;
import com.generador.horarios.proyecto.shift.ShiftSegment;
import com.generador.horarios.proyecto.venue.CoverageRequirement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Valida H1-H7 sobre un conjunto de ShiftAssignment (normalmente los de una
 * semana/Schedule). No depende de Spring: se puede instanciar y testear con
 * un simple "new ScheduleValidator()".
 *
 * <p>Dos nociones de tiempo distintas por cada ShiftAssignment, necesarias
 * porque un turno PARTIDO tiene un hueco sin pagar entre sus dos segmentos:
 * <ul>
 *   <li><b>trabajo efectivo</b> (H3, H4): suma de la duración de cada segmento,
 *       excluye el hueco.</li>
 *   <li><b>jornada</b> (H1, H2, H6): desde el inicio del primer segmento hasta
 *       el fin del último, incluye el hueco (es la misma jornada partida a
 *       efectos de descanso y solape).</li>
 * </ul>
 *
 * <p>H1 y H2 solo consideran las asignaciones recibidas en esta llamada; no
 * hay continuidad con la semana anterior/siguiente (eso es el histórico de
 * equidad de T2.6, una regla blanda distinta).
 */
public class ScheduleValidator {

    private static final int MIN_REST_HOURS = 12;
    private static final int MIN_WEEKLY_REST_HOURS = 36;
    private static final int MAX_DAILY_EFFECTIVE_HOURS = 9;
    private static final int MAX_WEEKLY_HOURS_FULL_TIME = 40;

    /**
     * @param weekStart lunes (00:00) de la semana ISO que se está validando; H2 lo
     *                  necesita para poder contar como descanso los días sin ninguna
     *                  asignación al principio o al final de la semana.
     */
    public List<ConstraintViolation> validate(
            List<ShiftAssignment> assignments, List<Preference> preferences,
            List<CoverageRequirement> coverageRequirements, LocalDate weekStart) {
        List<ConstraintViolation> violations = new ArrayList<>();
        violations.addAll(checkMinimumRestBetweenShifts(assignments));
        violations.addAll(checkWeeklyRest(assignments, weekStart));
        violations.addAll(checkMaxWeeklyHours(assignments));
        violations.addAll(checkMaxDailyEffectiveHours(assignments));
        violations.addAll(checkUnavailability(assignments, preferences));
        violations.addAll(checkOverlaps(assignments));
        violations.addAll(checkCoverage(assignments, coverageRequirements));
        return violations;
    }

    /** H1: mínimo 12h entre el fin de una jornada y el inicio de la siguiente. */
    private List<ConstraintViolation> checkMinimumRestBetweenShifts(List<ShiftAssignment> assignments) {
        List<ConstraintViolation> violations = new ArrayList<>();
        for (var entry : byEmployee(assignments).entrySet()) {
            List<ShiftAssignment> sorted = sortedByJornadaStart(entry.getValue());
            for (int i = 1; i < sorted.size(); i++) {
                LocalDateTime previousEnd = jornadaEnd(sorted.get(i - 1));
                LocalDateTime nextStart = jornadaStart(sorted.get(i));
                if (Duration.between(previousEnd, nextStart).toMinutes() < MIN_REST_HOURS * 60L) {
                    violations.add(new ConstraintViolation("H1", Severity.HARD,
                            "Descanso menor a " + MIN_REST_HOURS + "h entre turnos: termina " + previousEnd
                                    + " y empieza " + nextStart,
                            entry.getKey(), sorted.get(i).getDate()));
                }
            }
        }
        return violations;
    }

    /**
     * H2: al menos 36h de descanso continuo en la semana. Incluye el hueco antes
     * de la primera asignación y después de la última (acotados por weekStart y
     * weekStart+7 días), para que los días libres al borde de la semana cuenten.
     */
    private List<ConstraintViolation> checkWeeklyRest(List<ShiftAssignment> assignments, LocalDate weekStart) {
        LocalDateTime windowStart = weekStart.atStartOfDay();
        LocalDateTime windowEnd = weekStart.plusDays(7).atStartOfDay();

        List<ConstraintViolation> violations = new ArrayList<>();
        for (var entry : byEmployee(assignments).entrySet()) {
            List<ShiftAssignment> sorted = sortedByJornadaStart(entry.getValue());

            long maxGapMinutes = Duration.between(windowStart, jornadaStart(sorted.get(0))).toMinutes();
            for (int i = 1; i < sorted.size(); i++) {
                long gap = Duration.between(jornadaEnd(sorted.get(i - 1)), jornadaStart(sorted.get(i))).toMinutes();
                maxGapMinutes = Math.max(maxGapMinutes, gap);
            }
            long trailingGap = Duration.between(jornadaEnd(sorted.get(sorted.size() - 1)), windowEnd).toMinutes();
            maxGapMinutes = Math.max(maxGapMinutes, trailingGap);

            if (maxGapMinutes < MIN_WEEKLY_REST_HOURS * 60L) {
                violations.add(new ConstraintViolation("H2", Severity.HARD,
                        "No hay un descanso semanal continuo de al menos " + MIN_WEEKLY_REST_HOURS
                                + "h (máximo encontrado: " + (maxGapMinutes / 60.0) + "h)",
                        entry.getKey(), null));
            }
        }
        return violations;
    }

    /** H3: máximo 40h semanales, o contractHours si el empleado es PART_TIME. */
    private List<ConstraintViolation> checkMaxWeeklyHours(List<ShiftAssignment> assignments) {
        List<ConstraintViolation> violations = new ArrayList<>();
        for (var entry : byEmployee(assignments).entrySet()) {
            Employee employee = entry.getValue().get(0).getEmployee();
            long workedMinutes = entry.getValue().stream().mapToLong(this::effectiveMinutes).sum();
            int maxHours = employee.getContractType() == ContractType.PART_TIME && employee.getContractHours() != null
                    ? employee.getContractHours()
                    : MAX_WEEKLY_HOURS_FULL_TIME;
            if (workedMinutes > maxHours * 60L) {
                violations.add(new ConstraintViolation("H3", Severity.HARD,
                        "Supera las " + maxHours + "h semanales (tiene " + (workedMinutes / 60.0) + "h)",
                        entry.getKey(), null));
            }
        }
        return violations;
    }

    /** H4: máximo 9h de trabajo efectivo al día. */
    private List<ConstraintViolation> checkMaxDailyEffectiveHours(List<ShiftAssignment> assignments) {
        List<ConstraintViolation> violations = new ArrayList<>();
        Map<EmployeeDateKey, List<ShiftAssignment>> byEmployeeAndDate = assignments.stream()
                .collect(Collectors.groupingBy(a -> new EmployeeDateKey(a.getEmployee().getId(), a.getDate())));
        for (var entry : byEmployeeAndDate.entrySet()) {
            long workedMinutes = entry.getValue().stream().mapToLong(this::effectiveMinutes).sum();
            if (workedMinutes > MAX_DAILY_EFFECTIVE_HOURS * 60L) {
                violations.add(new ConstraintViolation("H4", Severity.HARD,
                        "Supera las " + MAX_DAILY_EFFECTIVE_HOURS + "h de trabajo efectivo el " + entry.getKey().date()
                                + " (tiene " + (workedMinutes / 60.0) + "h)",
                        entry.getKey().employeeId(), entry.getKey().date()));
            }
        }
        return violations;
    }

    /** H5: no asignar a alguien marcado UNAVAILABLE esa fecha. */
    private List<ConstraintViolation> checkUnavailability(List<ShiftAssignment> assignments, List<Preference> preferences) {
        Set<EmployeeDateKey> unavailable = preferences.stream()
                .filter(p -> p.getType() == PreferenceType.UNAVAILABLE)
                .map(p -> new EmployeeDateKey(p.getEmployee().getId(), p.getSpecificDate()))
                .collect(Collectors.toSet());

        List<ConstraintViolation> violations = new ArrayList<>();
        for (ShiftAssignment assignment : assignments) {
            EmployeeDateKey key = new EmployeeDateKey(assignment.getEmployee().getId(), assignment.getDate());
            if (unavailable.contains(key)) {
                violations.add(new ConstraintViolation("H5", Severity.HARD,
                        "Empleado marcado como no disponible el " + assignment.getDate(),
                        assignment.getEmployee().getId(), assignment.getDate()));
            }
        }
        return violations;
    }

    /** H6: no asignar dos turnos solapados a la misma persona. */
    private List<ConstraintViolation> checkOverlaps(List<ShiftAssignment> assignments) {
        List<ConstraintViolation> violations = new ArrayList<>();
        for (var entry : byEmployee(assignments).entrySet()) {
            List<ShiftAssignment> list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    LocalDateTime startA = jornadaStart(list.get(i));
                    LocalDateTime endA = jornadaEnd(list.get(i));
                    LocalDateTime startB = jornadaStart(list.get(j));
                    LocalDateTime endB = jornadaEnd(list.get(j));
                    if (startA.isBefore(endB) && startB.isBefore(endA)) {
                        violations.add(new ConstraintViolation("H6", Severity.HARD,
                                "Turnos solapados: " + startA + "-" + endA + " y " + startB + "-" + endB,
                                entry.getKey(), list.get(i).getDate()));
                    }
                }
            }
        }
        return violations;
    }

    /**
     * H7: cobertura mínima por (día de la semana, turno). Severidad SOFT: la
     * sección 7 del CLAUDE.md dice que el generador devuelve el DRAFT junto a
     * la lista de huecos sin cubrir, así que un hueco de cobertura no puede
     * bloquear el guardado como sí hacen H1-H6.
     */
    private List<ConstraintViolation> checkCoverage(List<ShiftAssignment> assignments, List<CoverageRequirement> requirements) {
        List<ConstraintViolation> violations = new ArrayList<>();
        for (CoverageRequirement requirement : requirements) {
            long assignedCount = assignments.stream()
                    .filter(a -> a.getDate().getDayOfWeek() == requirement.getDayOfWeek())
                    .filter(a -> a.getShiftTemplate().getId().equals(requirement.getShiftTemplate().getId()))
                    .count();
            if (assignedCount < requirement.getRequiredCount()) {
                violations.add(new ConstraintViolation("H7", Severity.SOFT,
                        "Cobertura insuficiente en " + requirement.getShiftTemplate().getName() + " el "
                                + requirement.getDayOfWeek() + ": hacen falta " + requirement.getRequiredCount()
                                + ", hay " + assignedCount,
                        null, null));
            }
        }
        return violations;
    }

    private Map<Long, List<ShiftAssignment>> byEmployee(List<ShiftAssignment> assignments) {
        return assignments.stream().collect(Collectors.groupingBy(a -> a.getEmployee().getId()));
    }

    private List<ShiftAssignment> sortedByJornadaStart(List<ShiftAssignment> assignments) {
        return assignments.stream().sorted(Comparator.comparing(this::jornadaStart)).toList();
    }

    private LocalDateTime jornadaStart(ShiftAssignment assignment) {
        LocalTime firstSegmentStart = assignment.getShiftTemplate().getSegments().get(0).getStartTime();
        return assignment.getDate().atTime(firstSegmentStart);
    }

    private LocalDateTime jornadaEnd(ShiftAssignment assignment) {
        List<ShiftSegment> segments = assignment.getShiftTemplate().getSegments();
        LocalTime lastSegmentEnd = segments.get(segments.size() - 1).getEndTime();
        return toDateTime(assignment.getDate(), lastSegmentEnd);
    }

    private long effectiveMinutes(ShiftAssignment assignment) {
        long total = 0;
        for (ShiftSegment segment : assignment.getShiftTemplate().getSegments()) {
            int startMinutes = segment.getStartTime().toSecondOfDay() / 60;
            int endMinutes = segment.getEndTime().equals(LocalTime.MIDNIGHT)
                    ? 24 * 60
                    : segment.getEndTime().toSecondOfDay() / 60;
            total += endMinutes - startMinutes;
        }
        return total;
    }

    private LocalDateTime toDateTime(LocalDate date, LocalTime time) {
        return time.equals(LocalTime.MIDNIGHT) ? date.plusDays(1).atStartOfDay() : date.atTime(time);
    }

    private record EmployeeDateKey(Long employeeId, LocalDate date) {
    }
}
