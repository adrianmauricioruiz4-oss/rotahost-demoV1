package com.generador.horarios.proyecto.schedule.dto;

import java.util.List;

public record ScheduleGenerationResponse(
        Long scheduleId,
        Long venueId,
        int isoYear,
        int isoWeek,
        String status,
        List<ShiftAssignmentResponse> assignments,
        List<UncoveredSlotResponse> uncoveredSlots,
        List<EquityReportEntryResponse> equityReport
) {
}
