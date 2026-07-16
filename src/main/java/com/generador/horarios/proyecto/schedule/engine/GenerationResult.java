package com.generador.horarios.proyecto.schedule.engine;

import com.generador.horarios.proyecto.shift.ShiftAssignment;
import java.util.List;

public record GenerationResult(List<ShiftAssignment> assignments, List<UncoveredSlot> uncoveredSlots) {
}
