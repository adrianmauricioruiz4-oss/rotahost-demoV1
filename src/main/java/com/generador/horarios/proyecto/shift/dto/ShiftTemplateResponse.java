package com.generador.horarios.proyecto.shift.dto;

import java.util.List;

public record ShiftTemplateResponse(
        Long id,
        String name,
        Long venueId,
        List<ShiftSegmentResponse> segments,
        boolean active
) {
}
