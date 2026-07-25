package com.generador.horarios.proyecto.schedule.dto;

import java.util.List;

public record SchedulePublishResponse(
        Long scheduleId,
        String status,
        List<String> softWarnings
) {
}
