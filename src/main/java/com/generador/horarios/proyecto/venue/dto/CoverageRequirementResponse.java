package com.generador.horarios.proyecto.venue.dto;

import java.time.DayOfWeek;

public record CoverageRequirementResponse(
        Long id,
        Long venueId,
        DayOfWeek dayOfWeek,
        Long shiftTemplateId,
        int requiredCount
) {
}
