package com.generador.horarios.proyecto.schedule.engine;

/**
 * Cuántos "turnos malos" (viernes/sábado noche, domingos) lleva un empleado:
 * solo en la semana que se acaba de generar, y contando también el
 * histórico de las 3 semanas anteriores que se pasó a generate(...).
 */
public record EquityReportEntry(Long employeeId, int badShiftsThisWeek, int badShiftsWithHistory) {
}
