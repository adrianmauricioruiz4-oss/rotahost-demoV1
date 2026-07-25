package com.generador.horarios.proyecto.schedule.dto;

import java.util.List;

/** assignment es null cuando la edición quitó el turno de esa celda. */
public record AssignmentEditResponse(
        ShiftAssignmentResponse assignment,
        List<String> softWarnings
) {
}
