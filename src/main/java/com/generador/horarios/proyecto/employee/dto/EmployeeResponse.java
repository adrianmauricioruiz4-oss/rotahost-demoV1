package com.generador.horarios.proyecto.employee.dto;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Position;
import java.util.Set;

public record EmployeeResponse(
        Long id,
        String name,
        String email,
        ContractType contractType,
        Integer contractHours,
        boolean active,
        Long venueId,
        Set<Position> positions
) {
}
