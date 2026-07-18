package com.generador.horarios.proyecto.schedule.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** shiftTemplateId a null quita la asignación de ese empleado/fecha en vez de sustituirla. */
public record EditAssignmentRequest(
        @NotNull Long employeeId,
        @NotNull LocalDate date,
        Long shiftTemplateId
) {
}
