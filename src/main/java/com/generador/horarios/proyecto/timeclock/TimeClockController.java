package com.generador.horarios.proyecto.timeclock;

import com.generador.horarios.proyecto.shared.security.CurrentUserService;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockEntryResponse;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockStatusResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/punch")
    public TimeClockEntryResponse punch(Authentication authentication) {
        Long employeeId = currentUserService.currentEmployee(authentication).getId();
        return timeClockService.punch(employeeId);
    }
}
