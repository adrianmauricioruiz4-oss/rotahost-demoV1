package com.generador.horarios.proyecto.schedule.dto;

import java.util.List;

public record ScheduleResponse(
        Long scheduleId,
        Long venueId,
        int isoYear,
        int isoWeek,
        String status,
        List<ShiftAssignmentResponse> assignments
) {
}
