package com.generador.horarios.proyecto.venue.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;

public record CoverageRequirementRequest(
        @NotNull Long venueId,
        @NotNull DayOfWeek dayOfWeek,
        @NotNull Long shiftTemplateId,
        @Min(1) int requiredCount
) {
}
