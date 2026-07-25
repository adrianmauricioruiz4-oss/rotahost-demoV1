package com.generador.horarios.proyecto.venue.dto;

import com.generador.horarios.proyecto.employee.Position;
import java.time.DayOfWeek;

public record CoverageRequirementResponse(
        Long id,
        Long venueId,
        DayOfWeek dayOfWeek,
        Long shiftTemplateId,
        int requiredCount,
        Position position
) {
}
