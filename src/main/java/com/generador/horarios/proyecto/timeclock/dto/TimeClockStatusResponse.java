package com.generador.horarios.proyecto.timeclock.dto;

import java.time.LocalDateTime;

/**
 * Estado de fichaje de una persona ahora mismo, con todo lo que la pantalla necesita para
 * pintarse sin hacer cuentas por su lado.
 *
 * @param state                 WORKING, ON_BREAK u OFF
 * @param nextAction            acción del botón principal: CLOCK_IN, CLOCK_OUT o BREAK_END
 * @param canStartBreak         si además cabe ofrecer "Hacer una pausa" (solo trabajando)
 * @param lastEntry             último fichaje registrado, o null si no ha fichado nunca
 * @param since                 hora en que empezó el estado actual, o null si está fuera de turno
 * @param workedTodayMinutes    minutos efectivos de hoy, ya descontado el exceso de pausa
 * @param breakTodayMinutes     minutos de pausa de hoy, se descuenten o no
 * @param workedThisWeekMinutes minutos efectivos de la semana ISO en curso
 * @param breakAllowanceMinutes minutos de pausa al día que este local reconoce como trabajados
 */
public record TimeClockStatusResponse(
        String state,
        String nextAction,
        boolean canStartBreak,
        TimeClockEntryResponse lastEntry,
        LocalDateTime since,
        long workedTodayMinutes,
        long breakTodayMinutes,
        long workedThisWeekMinutes,
        int breakAllowanceMinutes
) {
}
