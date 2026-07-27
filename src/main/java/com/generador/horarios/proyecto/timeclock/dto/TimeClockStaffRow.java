package com.generador.horarios.proyecto.timeclock.dto;

import java.time.LocalDateTime;

/**
 * Una persona en la consola de fichaje del encargado.
 *
 * @param state              WORKING, ON_BREAK u OFF
 * @param clockedInAt        hora de la última entrada, o null si hoy no ha fichado
 * @param workedTodayMinutes minutos efectivos de hoy
 * @param openShiftSince     hora de la entrada que dejó una jornada abierta de un día
 *                           anterior, o null si no hay ninguna. Es la incidencia que el
 *                           encargado tiene que resolver: alguien se fue sin fichar la salida.
 */
public record TimeClockStaffRow(
        Long employeeId,
        String name,
        String positions,
        String state,
        LocalDateTime clockedInAt,
        long workedTodayMinutes,
        LocalDateTime openShiftSince
) {
}
