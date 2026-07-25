package com.generador.horarios.proyecto.timeclock.dto;

import java.time.LocalDateTime;

public record TimeClockEntryResponse(Long id, String type, LocalDateTime timestamp) {
}
