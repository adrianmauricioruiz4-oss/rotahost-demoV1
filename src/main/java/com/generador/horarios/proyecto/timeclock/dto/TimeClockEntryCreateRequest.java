package com.generador.horarios.proyecto.timeclock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Fichaje que anota el encargado en nombre de un empleado. El caso real es la jornada que
 * quedó abierta porque alguien se fue sin fichar la salida: no hay nada que corregir, falta
 * un registro que hay que crear.
 *
 * @param employeeId a quién pertenece el fichaje
 * @param type       CLOCK_IN, BREAK_START, BREAK_END o CLOCK_OUT
 * @param timestamp  hora del fichaje
 * @param reason     por qué lo anota el encargado, obligatorio
 */
public record TimeClockEntryCreateRequest(
        @NotNull Long employeeId,
        @NotNull String type,
        @NotNull LocalDateTime timestamp,
        @NotBlank @Size(max = 500) String reason
) {
}
