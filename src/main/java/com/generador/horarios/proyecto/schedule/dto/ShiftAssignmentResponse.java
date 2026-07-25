package com.generador.horarios.proyecto.schedule.dto;

import java.time.LocalDate;

public record ShiftAssignmentResponse(Long id, Long employeeId, Long shiftTemplateId, LocalDate date) {
}
