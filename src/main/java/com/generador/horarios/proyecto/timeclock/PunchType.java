package com.generador.horarios.proyecto.timeclock;

/**
 * Los cuatro hitos de una jornada. La pausa se registra con su propio par de marcas y no
 * como una salida seguida de una entrada: son cosas distintas a efectos de recuento, y el
 * trabajador tiene derecho a ver en qué se le va el tiempo.
 */
public enum PunchType {
    CLOCK_IN,
    BREAK_START,
    BREAK_END,
    CLOCK_OUT
}
