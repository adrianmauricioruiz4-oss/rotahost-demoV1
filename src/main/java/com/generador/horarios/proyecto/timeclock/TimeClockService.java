package com.generador.horarios.proyecto.timeclock;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockEntryResponse;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockStatusResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fichaje de jornada: entrada, pausa, vuelta y salida. No valida contra el cuadrante (podría
 * cruzarse con él más adelante); de momento registra el hecho de fichar y cuenta el tiempo.
 *
 * <p>Las pausas se guardan como marcas propias y solo restan del cómputo en lo que excedan
 * del margen que reconoce el local ({@code breakAllowanceMinutes} del Venue). Ver
 * {@link WorkedTime} para el detalle del recuento.
 */
@Service
public class TimeClockService {

    /**
     * Margen hacia atrás al buscar fichajes de un día o una semana: una jornada de noche
     * empieza un día y termina al siguiente, así que hay que mirar antes del corte para
     * encontrar la entrada que la abrió.
     */
    private static final int LOOKBACK_DAYS = 2;

    private final TimeClockEntryRepository timeClockEntryRepository;
    private final EmployeeRepository employeeRepository;

    public TimeClockService(TimeClockEntryRepository timeClockEntryRepository, EmployeeRepository employeeRepository) {
        this.timeClockEntryRepository = timeClockEntryRepository;
        this.employeeRepository = employeeRepository;
    }

    /** Estado actual de una persona, con el acumulado del día y de la semana ISO en curso. */
    @Transactional(readOnly = true)
    public TimeClockStatusResponse status(Long employeeId) {
        return status(employeeId, LocalDateTime.now());
    }

    /** Igual que {@link #status(Long)}, con el "ahora" inyectado para poder probarlo. */
    @Transactional(readOnly = true)
    public TimeClockStatusResponse status(Long employeeId, LocalDateTime now) {
        Optional<TimeClockEntry> last = timeClockEntryRepository.findTopByEmployeeIdOrderByTimestampDesc(employeeId);
        PunchType lastType = last.map(TimeClockEntry::getType).orElse(null);
        int allowance = breakAllowanceOf(employeeId);

        // Semana ISO (lunes a domingo), la misma que usan los cuadrantes.
        LocalDate today = now.toLocalDate();
        LocalDate weekStart = today.with(WeekFields.ISO.dayOfWeek(), 1);

        long workedToday = 0;
        long breakToday = 0;
        long workedWeek = 0;
        for (LocalDate day = weekStart; !day.isAfter(today); day = day.plusDays(1)) {
            WorkedTime.Spans spans = spansAround(employeeId, day, now);
            LocalDateTime from = day.atStartOfDay();
            LocalDateTime to = day.plusDays(1).atStartOfDay();
            long effective = WorkedTime.effectiveMinutes(spans, from, to, allowance);
            workedWeek += effective;
            if (day.equals(today)) {
                workedToday = effective;
                breakToday = WorkedTime.breakMinutes(spans, from, to);
            }
        }

        return new TimeClockStatusResponse(
                stateOf(lastType).name(),
                nextActionAfter(lastType).name(),
                stateOf(lastType) == ClockState.WORKING,
                last.map(this::toResponse).orElse(null),
                stateOf(lastType) == ClockState.OFF ? null : last.map(TimeClockEntry::getTimestamp).orElse(null),
                workedToday,
                breakToday,
                workedWeek,
                allowance);
    }

    /**
     * Fichajes de una persona en un día concreto, en orden. Es lo que la pantalla del
     * empleado enseña como línea de tiempo: el trabajador tiene derecho a ver sus registros.
     */
    @Transactional(readOnly = true)
    public List<TimeClockEntryResponse> entriesOn(Long employeeId, LocalDate date) {
        return timeClockEntryRepository.findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(
                        employeeId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream().map(this::toResponse).toList();
    }

    /** Ficha la acción que toque según el último fichaje. */
    @Transactional
    public TimeClockEntryResponse punch(Long employeeId) {
        return punch(employeeId, null);
    }

    /**
     * Ficha una acción concreta. Con {@code requested} a null se usa la que toque; si se pide
     * una explícita, se rechaza cuando no encaja con el estado actual — no se puede empezar una
     * pausa sin haber entrado, ni entrar dos veces seguidas.
     *
     * @param requested acción pedida, o null para la que toque por orden
     * @throws ResponseStatusException 409 si la acción pedida no encaja con el estado actual
     */
    @Transactional
    public TimeClockEntryResponse punch(Long employeeId, PunchType requested) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empleado no encontrado: " + employeeId));
        Optional<TimeClockEntry> last = timeClockEntryRepository.findTopByEmployeeIdOrderByTimestampDesc(employeeId);
        PunchType lastType = last.map(TimeClockEntry::getType).orElse(null);

        PunchType type = requested == null ? nextActionAfter(lastType) : requested;
        if (!isAllowed(lastType, type)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflictMessage(stateOf(lastType), type));
        }
        TimeClockEntry saved = timeClockEntryRepository.save(new TimeClockEntry(employee, type, LocalDateTime.now()));
        return toResponse(saved);
    }

    /** En qué situación deja a la persona su último fichaje. */
    private enum ClockState {
        WORKING, ON_BREAK, OFF
    }

    private static ClockState stateOf(PunchType lastType) {
        if (lastType == null || lastType == PunchType.CLOCK_OUT) {
            return ClockState.OFF;
        }
        return lastType == PunchType.BREAK_START ? ClockState.ON_BREAK : ClockState.WORKING;
    }

    /**
     * Acción principal que toca. Trabajando ofrece la salida, no la pausa: la pausa es la
     * acción secundaria y se pide explícitamente (ver {@code canStartBreak}).
     */
    private static PunchType nextActionAfter(PunchType lastType) {
        return switch (stateOf(lastType)) {
            case OFF -> PunchType.CLOCK_IN;
            case ON_BREAK -> PunchType.BREAK_END;
            case WORKING -> PunchType.CLOCK_OUT;
        };
    }

    private static boolean isAllowed(PunchType lastType, PunchType requested) {
        ClockState state = stateOf(lastType);
        return switch (requested) {
            case CLOCK_IN -> state == ClockState.OFF;
            case CLOCK_OUT, BREAK_START -> state == ClockState.WORKING;
            case BREAK_END -> state == ClockState.ON_BREAK;
        };
    }

    private static String conflictMessage(ClockState state, PunchType requested) {
        if (state == ClockState.ON_BREAK && requested == PunchType.CLOCK_OUT) {
            return "Para fichar la salida, primero termina la pausa.";
        }
        return switch (state) {
            case OFF -> "No has fichado la entrada todavía.";
            case ON_BREAK -> "Estás en una pausa.";
            case WORKING -> "Ya has fichado la entrada.";
        };
    }

    /**
     * Fichajes que pueden afectar a un día, incluidos los de la víspera: una jornada de noche
     * arranca antes de medianoche y sus minutos posteriores cuentan en el día siguiente.
     */
    private WorkedTime.Spans spansAround(Long employeeId, LocalDate day, LocalDateTime now) {
        List<TimeClockEntry> entries = timeClockEntryRepository.findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(
                employeeId, day.minusDays(LOOKBACK_DAYS).atStartOfDay(), day.plusDays(1).atStartOfDay());
        return WorkedTime.toSpans(entries, now);
    }

    /** Margen de pausa del local al que pertenece la persona. */
    private int breakAllowanceOf(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .map(employee -> employee.getVenue().getBreakAllowanceMinutes())
                .orElse(0);
    }

    private TimeClockEntryResponse toResponse(TimeClockEntry entry) {
        return new TimeClockEntryResponse(entry.getId(), entry.getType().name(), entry.getTimestamp());
    }
}
