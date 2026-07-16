package com.generador.horarios.proyecto.venue;

import java.time.DayOfWeek;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverageRequirementRepository extends JpaRepository<CoverageRequirement, Long> {

    Optional<CoverageRequirement> findByVenueIdAndDayOfWeekAndShiftTemplateId(
            Long venueId, DayOfWeek dayOfWeek, Long shiftTemplateId);
}
