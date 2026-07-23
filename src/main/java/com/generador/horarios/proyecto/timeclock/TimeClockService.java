package com.generador.horarios.proyecto.timeclock;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockEntryResponse;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockStatusResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fichaje simple: alterna ENTRADA/SALIDA por empleado. No valida contra el cuadrante (podría
 * cruzarse con él más adelante); de momento solo registra el hecho de fichar.
 */
@Service
public class TimeClockService {

    private final TimeClockEntryRepository timeClockEntryRepository;
    private final EmployeeRepository employeeRepository;

    public TimeClockService(TimeClockEntryRepository timeClockEntryRepository, EmployeeRepository employeeRepository) {
        this.timeClockEntryRepository = timeClockEntryRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public TimeClockStatusResponse status(Long employeeId) {
        Optional<TimeClockEntry> last = timeClockEntryRepository.findTopByEmployeeIdOrderByTimestampDesc(employeeId);
        PunchType nextAction = nextActionAfter(last.map(TimeClockEntry::getType).orElse(null));
        return new TimeClockStatusResponse(nextAction.name(), last.map(this::toResponse).orElse(null));
    }

    @Transactional
    public TimeClockEntryResponse punch(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empleado no encontrado: " + employeeId));
        Optional<TimeClockEntry> last = timeClockEntryRepository.findTopByEmployeeIdOrderByTimestampDesc(employeeId);
        PunchType type = nextActionAfter(last.map(TimeClockEntry::getType).orElse(null));
        TimeClockEntry saved = timeClockEntryRepository.save(new TimeClockEntry(employee, type, LocalDateTime.now()));
        return toResponse(saved);
    }

    /** Sin fichajes previos -> CLOCK_IN. Después alterna sin más (no hay "olvidé fichar salida"). */
    private PunchType nextActionAfter(PunchType lastType) {
        return lastType == PunchType.CLOCK_IN ? PunchType.CLOCK_OUT : PunchType.CLOCK_IN;
    }

    private TimeClockEntryResponse toResponse(TimeClockEntry entry) {
        return new TimeClockEntryResponse(entry.getId(), entry.getType().name(), entry.getTimestamp());
    }
}
