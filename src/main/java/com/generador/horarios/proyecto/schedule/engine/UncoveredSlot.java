package com.generador.horarios.proyecto.schedule.engine;

import java.time.LocalDate;

/** Un (fecha, turno) para el que faltaron "missing" personas por cubrir. */
public record UncoveredSlot(LocalDate date, Long shiftTemplateId, int missing) {
}
