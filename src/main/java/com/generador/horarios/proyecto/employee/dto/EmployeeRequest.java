package com.generador.horarios.proyecto.employee.dto;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Position;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.Set;

/**
 * contractHours no se valida aquí con @NotNull porque su obligatoriedad
 * depende de contractType (PART_TIME); esa regla cruzada vive en EmployeeService.
 * positions puede venir vacío o null (empleado sin puesto asignado todavía).
 */
public record EmployeeRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotNull ContractType contractType,
        Integer contractHours,
        @NotNull Long venueId,
        Set<Position> positions,
        boolean canWorkSplitShift,
        boolean canOpen,
        boolean canClose,
        LocalTime minEntryTime,
        LocalTime maxExitTime,
        String internalNotes
) {
}
