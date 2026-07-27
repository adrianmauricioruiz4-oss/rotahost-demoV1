package com.generador.horarios.proyecto.timeclock.dto;

import java.time.LocalDateTime;

/**
 * Un fichaje, con su rastro de corrección si lo han retocado. Los campos de corrección se
 * devuelven siempre: quien mira un registro tiene que poder ver si es lo que se fichó o lo
 * que alguien anotó después, y por qué.
 *
 * @param originalTimestamp hora que tenía antes de la primera corrección, o null si nadie lo tocó
 * @param correctionReason  motivo de la corrección, o null
 * @param correctedByName   nombre del encargado que la hizo, o null
 * @param addedByManager    true si lo anotó el encargado y no el empleado al fichar
 */
public record TimeClockEntryResponse(
        Long id,
        String type,
        LocalDateTime timestamp,
        LocalDateTime originalTimestamp,
        LocalDateTime correctedAt,
        String correctedByName,
        String correctionReason,
        boolean addedByManager
) {
}
