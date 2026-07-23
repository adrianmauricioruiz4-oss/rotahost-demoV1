package com.generador.horarios.proyecto.shared.security.dto;

import com.generador.horarios.proyecto.employee.EmployeeRole;

public record AuthMeResponse(
        Long employeeId,
        String name,
        String email,
        EmployeeRole role,
        Long venueId,
        boolean guest
) {
}
