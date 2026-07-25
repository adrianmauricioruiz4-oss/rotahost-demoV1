package com.generador.horarios.proyecto.preference.dto;

import com.generador.horarios.proyecto.preference.PreferenceType;
import java.time.DayOfWeek;
import java.time.LocalDate;

public record PreferenceResponse(
        Long id,
        Long employeeId,
        PreferenceType type,
        DayOfWeek dayOfWeek,
        Long shiftTemplateId,
        LocalDate specificDate,
        int weight
) {
}
