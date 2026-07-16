package com.generador.horarios.proyecto.shift.dto;

import java.time.LocalTime;

public record ShiftSegmentResponse(LocalTime startTime, LocalTime endTime) {
}
