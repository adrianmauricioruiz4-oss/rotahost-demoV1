package com.generador.horarios.proyecto.timeclock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Corrección de un fichaje existente. El motivo es obligatorio a propósito: un registro
 * horario retocado sin explicación no vale de nada ante una inspección.
 *
 * @param timestamp hora correcta
 * @param reason    por qué se cambia, en lenguaje llano
 */
public record TimeClockCorrectionRequest(
        @NotNull LocalDateTime timestamp,
        @NotBlank @Size(max = 500) String reason
) {
}
