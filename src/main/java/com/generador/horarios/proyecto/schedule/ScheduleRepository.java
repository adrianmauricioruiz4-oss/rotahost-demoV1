package com.generador.horarios.proyecto.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    boolean existsByVenueIdAndIsoYearAndIsoWeek(Long venueId, int isoYear, int isoWeek);
}
