package com.generador.horarios.proyecto.venue;

import java.time.DayOfWeek;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverageRequirementRepository extends JpaRepository<CoverageRequirement, Long> {

    /** Puede haber varias filas por (venue, día, turno): una por puesto, desde T5.3. */
    List<CoverageRequirement> findByVenueIdAndDayOfWeekAndShiftTemplateId(
            Long venueId, DayOfWeek dayOfWeek, Long shiftTemplateId);

    List<CoverageRequirement> findByVenueId(Long venueId);
}
