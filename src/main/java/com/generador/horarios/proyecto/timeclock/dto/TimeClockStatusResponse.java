package com.generador.horarios.proyecto.timeclock.dto;

/** nextAction: "CLOCK_IN" o "CLOCK_OUT", según cuál fue el último fichaje (o CLOCK_IN si no hay ninguno). */
public record TimeClockStatusResponse(String nextAction, TimeClockEntryResponse lastEntry) {
}
