package com.generador.horarios.proyecto.employee.dto;

import com.generador.horarios.proyecto.employee.ContractType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * contractHours no se valida aquí con @NotNull porque su obligatoriedad
 * depende de contractType (PART_TIME); esa regla cruzada vive en EmployeeService.
 */
public record EmployeeRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotNull ContractType contractType,
        Integer contractHours,
        @NotNull Long venueId
) {
}
