package com.generador.horarios.proyecto.preference.dto;

import com.generador.horarios.proyecto.preference.PreferenceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Solo uno de dayOfWeek, shiftTemplateId o specificDate debe venir informado,
 * según type; weight solo aplica a los tipos blandos. Esa coherencia se
 * valida en PreferenceService, no aquí.
 */
public record PreferenceRequest(
        @NotNull Long employeeId,
        @NotNull PreferenceType type,
        DayOfWeek dayOfWeek,
        Long shiftTemplateId,
        LocalDate specificDate,
        @Min(1) @Max(5) Integer weight
) {
}
