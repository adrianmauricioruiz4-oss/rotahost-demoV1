package com.generador.horarios.proyecto.dashboard.dto;

import java.util.List;

/**
 * Resumen del venue propio para la pantalla de inicio (T4.6). scheduleStatus es
 * null cuando todavía no se ha generado cuadrante para la semana ISO actual;
 * en ese caso alerts trae un único aviso explicándolo en vez de una lista vacía.
 */
public record DashboardSummaryResponse(
        String venueName,
        long employeeCount,
        int isoYear,
        int isoWeek,
        String scheduleStatus,
        List<String> alerts
) {
}
