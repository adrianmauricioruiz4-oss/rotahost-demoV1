package com.generador.horarios.proyecto.schedule.engine;

import java.time.LocalDate;

/**
 * employeeId y date son nullable: H7 (cobertura) no señala a un empleado
 * concreto, así que ambos pueden venir vacíos en ese caso.
 */
public record ConstraintViolation(
        String ruleId,
        Severity severity,
        String message,
        Long employeeId,
        LocalDate date
) {
}
