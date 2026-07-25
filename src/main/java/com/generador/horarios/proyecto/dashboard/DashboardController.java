package com.generador.horarios.proyecto.dashboard;

import com.generador.horarios.proyecto.dashboard.dto.DashboardSummaryResponse;
import com.generador.horarios.proyecto.shared.security.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resumen del venue del usuario autenticado (T4.6). A diferencia de VenueController o
 * ScheduleController no recibe un id por parámetro: siempre es el venue de quien pregunta,
 * así que no hay nada que comprobar con requireOwnVenue.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserService currentUserService;

    public DashboardController(DashboardService dashboardService, CurrentUserService currentUserService) {
        this.dashboardService = dashboardService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(Authentication authentication) {
        Long venueId = currentUserService.currentVenueId(authentication);
        return dashboardService.getSummary(venueId);
    }
}
