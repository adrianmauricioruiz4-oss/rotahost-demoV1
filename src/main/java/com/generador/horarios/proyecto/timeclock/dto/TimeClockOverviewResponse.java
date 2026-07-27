package com.generador.horarios.proyecto.timeclock.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Quién está trabajando ahora mismo en el local. Es lo que el encargado mira de un vistazo
 * detrás de la barra.
 *
 * @param date            día al que se refiere el recuento
 * @param working         cuántos están trabajando
 * @param onBreak         cuántos están en una pausa
 * @param notClockedIn    cuántos no han fichado hoy
 * @param openShiftsCount cuántas jornadas quedaron abiertas de días anteriores
 * @param staff           una fila por persona activa del local
 */
public record TimeClockOverviewResponse(
        LocalDate date,
        int working,
        int onBreak,
        int notClockedIn,
        int openShiftsCount,
        List<TimeClockStaffRow> staff
) {
}
