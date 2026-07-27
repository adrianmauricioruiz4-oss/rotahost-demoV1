package com.generador.horarios.proyecto.timeclock;

import com.generador.horarios.proyecto.shared.security.CurrentUserService;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockCorrectionRequest;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockEntryCreateRequest;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockEntryResponse;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockOverviewResponse;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockStatusResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fichar entrada/salida. Cualquier cuenta autenticada puede fichar por sí misma, invitados incluidos. */
@RestController
@RequestMapping("/api/timeclock")
public class TimeClockController {

    private final TimeClockService timeClockService;
    private final CurrentUserService currentUserService;

    public TimeClockController(TimeClockService timeClockService, CurrentUserService currentUserService) {
        this.timeClockService = timeClockService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/status")
    public TimeClockStatusResponse status(Authentication authentication) {
        Long employeeId = currentUserService.currentEmployee(authentication).getId();
        return timeClockService.status(employeeId);
    }

    /**
     * Fichajes propios de un día, para la línea de tiempo. Solo los de quien pregunta: para
     * ver los de otra persona está la consola del encargado.
     *
     * @param date día a consultar; si falta, hoy
     */
    @GetMapping("/entries")
    public List<TimeClockEntryResponse> entries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        Long employeeId = currentUserService.currentEmployee(authentication).getId();
        return timeClockService.entriesOn(employeeId, date == null ? LocalDate.now() : date);
    }

    /**
     * Ficha. Sin cuerpo hace la acción que toque; con {@code type} hace la pedida, que es
     * como la pantalla pide una pausa: trabajando, la acción "que toca" es la salida.
     */
    @PostMapping("/punch")
    public TimeClockEntryResponse punch(
            @RequestBody(required = false) PunchRequest request, Authentication authentication) {
        Long employeeId = currentUserService.currentEmployee(authentication).getId();
        return timeClockService.punch(employeeId, request == null ? null : request.type());
    }

    /**
     * Quién está trabajando ahora mismo. Siempre del local de quien pregunta: el venue no
     * viaja como parámetro para que nadie pueda mirar otro cambiando la URL.
     */
    @GetMapping("/overview")
    public TimeClockOverviewResponse overview(Authentication authentication) {
        Long venueId = currentUserService.currentVenueId(authentication);
        return timeClockService.overview(venueId, LocalDateTime.now());
    }

    /**
     * Corrige la hora de un fichaje. Solo el encargado, solo de su local, y con motivo
     * obligatorio. No borra nada: el registro guarda su hora original y quién lo cambió.
     */
    @PutMapping("/entries/{entryId}")
    public TimeClockEntryResponse correct(
            @PathVariable Long entryId,
            @Valid @RequestBody TimeClockCorrectionRequest request,
            Authentication authentication) {
        return timeClockService.correct(entryId, request, currentUserService.currentEmployee(authentication));
    }

    /**
     * Anota un fichaje que falta, en nombre de un empleado. Es lo que cierra una jornada que
     * quedó abierta porque alguien se fue sin fichar la salida.
     */
    @PostMapping("/entries")
    public TimeClockEntryResponse addOnBehalf(
            @Valid @RequestBody TimeClockEntryCreateRequest request, Authentication authentication) {
        return timeClockService.addOnBehalf(request, currentUserService.currentEmployee(authentication));
    }

    /** @param type acción concreta a registrar, o null para la que toque por orden. */
    public record PunchRequest(PunchType type) {
    }
}
