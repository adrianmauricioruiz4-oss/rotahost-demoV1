package com.generador.horarios.proyecto.venue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalTime;

/**
 * @param breakAllowanceMinutes minutos de pausa al día que el local reconoce como tiempo
 *                              trabajado. Null deja el valor que ya tuviera el venue.
 */
public record VenueRequest(
        @NotBlank String name,
        @NotNull LocalTime openingTime,
        @NotNull LocalTime closingTime,
        @PositiveOrZero @Max(480) Integer breakAllowanceMinutes
) {
}
