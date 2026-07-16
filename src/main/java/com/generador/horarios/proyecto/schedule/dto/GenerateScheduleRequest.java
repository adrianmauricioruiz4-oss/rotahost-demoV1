package com.generador.horarios.proyecto.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GenerateScheduleRequest(
        @NotNull Long venueId,
        @Min(2000) int isoYear,
        @Min(1) @Max(53) int isoWeek
) {
}
