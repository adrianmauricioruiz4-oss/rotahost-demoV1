package com.generador.horarios.proyecto.shift.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * endTime puede ser 00:00 para representar un turno que cruza la medianoche
 * (ej. TARDE 16:00-00:00); esa regla se valida en ShiftTemplateService, no aquí.
 */
public record ShiftSegmentRequest(
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {
}
