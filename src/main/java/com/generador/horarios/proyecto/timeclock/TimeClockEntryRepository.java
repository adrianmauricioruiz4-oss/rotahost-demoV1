package com.generador.horarios.proyecto.timeclock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeClockEntryRepository extends JpaRepository<TimeClockEntry, Long> {

    Optional<TimeClockEntry> findTopByEmployeeIdOrderByTimestampDesc(Long employeeId);

    /**
     * Fichajes de una persona en una ventana de tiempo, del más antiguo al más reciente.
     * El orden importa: el recuento de jornada empareja las marcas recorriéndolas en orden.
     */
    List<TimeClockEntry> findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(
            Long employeeId, LocalDateTime from, LocalDateTime to);
}
