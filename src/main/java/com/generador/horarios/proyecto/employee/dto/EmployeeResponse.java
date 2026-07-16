package com.generador.horarios.proyecto.employee.dto;

import com.generador.horarios.proyecto.employee.ContractType;

public record EmployeeResponse(
        Long id,
        String name,
        String email,
        ContractType contractType,
        Integer contractHours,
        boolean active,
        Long venueId
) {
}
