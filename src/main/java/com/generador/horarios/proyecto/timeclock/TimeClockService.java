package com.generador.horarios.proyecto.timeclock;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockCorrectionRequest;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockEntryCreateRequest;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockEntryResponse;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockOverviewResponse;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockStaffRow;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockStatusResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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

    /**
     * Quién está trabajando ahora mismo en un local, con su acumulado del día y las jornadas
     * que quedaron abiertas de días anteriores.
     *
     * @param venueId local del encargado que pregunta
     */
    @Transactional(readOnly = true)
    public TimeClockOverviewResponse overview(Long venueId, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<TimeClockStaffRow> staff = employeeRepository.findByVenueIdAndActiveTrue(venueId).stream()
                .map(employee -> staffRow(employee, today, now))
                .sorted(Comparator.comparing(TimeClockStaffRow::name))
                .toList();

        return new TimeClockOverviewResponse(
                today,
                (int) staff.stream().filter(r -> "WORKING".equals(r.state())).count(),
                (int) staff.stream().filter(r -> "ON_BREAK".equals(r.state())).count(),
                (int) staff.stream().filter(r -> "OFF".equals(r.state())).count(),
                (int) staff.stream().filter(r -> r.openShiftSince() != null).count(),
                staff);
    }

    private TimeClockStaffRow staffRow(Employee employee, LocalDate today, LocalDateTime now) {
        Optional<TimeClockEntry> last = timeClockEntryRepository.findTopByEmployeeIdOrderByTimestampDesc(employee.getId());
        PunchType lastType = last.map(TimeClockEntry::getType).orElse(null);
        ClockState state = stateOf(lastType);

        WorkedTime.Spans spans = spansAround(employee.getId(), today, now);
        long workedToday = WorkedTime.effectiveMinutes(
                spans, today.atStartOfDay(), today.plusDays(1).atStartOfDay(),
                employee.getVenue().getBreakAllowanceMinutes());

        // Sigue dentro y su última marca es de otro día: se fue sin fichar la salida.
        LocalDateTime openShiftSince = null;
        if (state != ClockState.OFF && last.isPresent() && last.get().getTimestamp().toLocalDate().isBefore(today)) {
            openShiftSince = last.get().getTimestamp();
        }

        LocalDateTime clockedInAt = state == ClockState.OFF
                ? null
                : last.map(TimeClockEntry::getTimestamp).orElse(null);

        String positions = employee.getPositions().stream()
                .map(Enum::name).sorted().collect(Collectors.joining(", "));

        return new TimeClockStaffRow(
                employee.getId(), employee.getName(), positions,
                state.name(), clockedInAt, workedToday, openShiftSince);
    }

    /**
     * Corrige la hora de un fichaje existente. No sustituye el registro: guarda la hora
     * original, quién lo cambió, cuándo y por qué.
     *
     * @param manager encargado que hace el cambio; solo puede tocar fichajes de su propio local
     * @throws ResponseStatusException 404 si no existe, 403 si es de otro local
     */
    @Transactional
    public TimeClockEntryResponse correct(Long entryId, TimeClockCorrectionRequest request, Employee manager) {
        TimeClockEntry entry = timeClockEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichaje no encontrado: " + entryId));
        requireSameVenue(entry.getEmployee(), manager);

        entry.correctTo(request.timestamp(), manager, request.reason(), LocalDateTime.now());
        return toResponse(entry);
    }

    /**
     * Anota un fichaje que falta, en nombre de un empleado. Es lo que resuelve la jornada que
     * quedó abierta: no hay nada que corregir, falta la salida que nadie fichó.
     *
     * @param manager encargado que lo anota; solo sobre empleados de su propio local
     * @throws ResponseStatusException 404 si el empleado no existe, 403 si es de otro local,
     *                                 400 si el tipo de fichaje no es válido
     */
    @Transactional
    public TimeClockEntryResponse addOnBehalf(TimeClockEntryCreateRequest request, Employee manager) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Empleado no encontrado: " + request.employeeId()));
        requireSameVenue(employee, manager);

        PunchType type;
        try {
            type = PunchType.valueOf(request.type());
        } catch (IllegalArgumentException notAPunchType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de fichaje desconocido: " + request.type());
        }

        TimeClockEntry entry = new TimeClockEntry(employee, type, request.timestamp(), true);
        entry.annotate(manager, request.reason(), LocalDateTime.now());
        return toResponse(timeClockEntryRepository.save(entry));
    }

    /** Multi-tenant: un encargado no toca fichajes de un local que no es el suyo. */
    private void requireSameVenue(Employee target, Employee manager) {
        Long targetVenueId = target.getVenue() == null ? null : target.getVenue().getId();
        Long managerVenueId = manager.getVenue() == null ? null : manager.getVenue().getId();
        if (targetVenueId == null || !targetVenueId.equals(managerVenueId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ese fichaje no es de tu local.");
        }
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
        return new TimeClockEntryResponse(
                entry.getId(),
                entry.getType().name(),
                entry.getTimestamp(),
                entry.getOriginalTimestamp(),
                entry.getCorrectedAt(),
                entry.getCorrectedBy() == null ? null : entry.getCorrectedBy().getName(),
                entry.getCorrectionReason(),
                entry.isAddedByManager());
    }
}
