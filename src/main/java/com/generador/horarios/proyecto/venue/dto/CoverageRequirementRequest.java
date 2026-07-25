package com.generador.horarios.proyecto.venue.dto;

import com.generador.horarios.proyecto.employee.Position;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;

/** position null = requisito general (cualquier puesto), como antes de T5.3. */
public record CoverageRequirementRequest(
        @NotNull Long venueId,
        @NotNull DayOfWeek dayOfWeek,
        @NotNull Long shiftTemplateId,
        @Min(1) int requiredCount,
        Position position
) {
}
