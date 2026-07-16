package com.generador.horarios.proyecto.schedule.dto;

import java.time.LocalDate;

public record UncoveredSlotResponse(LocalDate date, Long shiftTemplateId, int missing) {
}
