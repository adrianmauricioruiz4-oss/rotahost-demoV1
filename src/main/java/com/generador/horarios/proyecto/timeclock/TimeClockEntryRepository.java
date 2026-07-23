package com.generador.horarios.proyecto.timeclock;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeClockEntryRepository extends JpaRepository<TimeClockEntry, Long> {

    Optional<TimeClockEntry> findTopByEmployeeIdOrderByTimestampDesc(Long employeeId);
}
