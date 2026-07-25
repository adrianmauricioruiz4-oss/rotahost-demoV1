package com.generador.horarios.proyecto.shared;

import java.time.Instant;
import java.util.List;

/** Forma uniforme de cualquier respuesta de error de la API. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
}
