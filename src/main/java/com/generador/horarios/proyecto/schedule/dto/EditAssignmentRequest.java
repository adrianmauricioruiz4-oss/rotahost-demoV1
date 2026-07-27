package com.generador.horarios.proyecto.schedule.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * shiftTemplateId a null quita la asignación de ese empleado/fecha en vez de sustituirla.
 *
 * @param lastMinute cambio de última hora sobre un cuadrante ya publicado. Va aparte a
 *                   propósito: publicar bloquea la edición (T3.4), y tocar algo que el
 *                   equipo ya ha visto tiene que ser una decisión deliberada del encargado,
 *                   no un clic que se cuela. Null equivale a false.
 */
public record EditAssignmentRequest(
        @NotNull Long employeeId,
        @NotNull LocalDate date,
        Long shiftTemplateId,
        Boolean lastMinute
) {

    public boolean isLastMinute() {
        return Boolean.TRUE.equals(lastMinute);
    }
}
