package com.generador.horarios.proyecto.preference;

/**
 * UNAVAILABLE es restricción dura (H5); el resto son blandas (S1). Cada tipo
 * usa un campo distinto de Preference: PREFERS_DAY/AVOIDS_DAY -> dayOfWeek,
 * PREFERS_SHIFT/AVOIDS_SHIFT -> shiftTemplate, UNAVAILABLE -> specificDate.
 */
public enum PreferenceType {
    PREFERS_DAY,
    AVOIDS_DAY,
    PREFERS_SHIFT,
    AVOIDS_SHIFT,
    UNAVAILABLE
}
