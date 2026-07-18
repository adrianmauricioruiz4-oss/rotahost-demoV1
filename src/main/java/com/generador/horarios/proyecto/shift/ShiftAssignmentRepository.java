package com.generador.horarios.proyecto.shift;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    List<ShiftAssignment> findBySchedule_Venue_IdAndDateBetween(Long venueId, LocalDate startInclusive, LocalDate endInclusive);

    List<ShiftAssignment> findByScheduleId(Long scheduleId);
}
