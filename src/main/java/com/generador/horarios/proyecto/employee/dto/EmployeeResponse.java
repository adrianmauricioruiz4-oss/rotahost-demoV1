package com.generador.horarios.proyecto.employee.dto;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Position;
import java.time.LocalTime;
import java.util.Set;

public record EmployeeResponse(
        Long id,
        String name,
        String email,
        ContractType contractType,
        Integer contractHours,
        boolean active,
        Long venueId,
        Set<Position> positions,
        boolean canWorkSplitShift,
        boolean canOpen,
        boolean canClose,
        LocalTime minEntryTime,
        LocalTime maxExitTime,
        String internalNotes
) {
}
