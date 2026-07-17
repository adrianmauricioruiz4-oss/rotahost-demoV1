package com.generador.horarios.proyecto.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record VenueRequest(
        @NotBlank String name,
        @NotNull LocalTime openingTime,
        @NotNull LocalTime closingTime
) {
}
