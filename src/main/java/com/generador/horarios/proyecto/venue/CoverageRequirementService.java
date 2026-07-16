package com.generador.horarios.proyecto.venue;

import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.shift.ShiftTemplateRepository;
import com.generador.horarios.proyecto.venue.dto.CoverageRequirementRequest;
import com.generador.horarios.proyecto.venue.dto.CoverageRequirementResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lógica de negocio del CRUD de CoverageRequirement: valida venue y
 * shiftTemplate existentes y coherentes, y evita duplicar la misma
 * combinación (venue, día de la semana, shiftTemplate).
 */
@Service
public class CoverageRequirementService {

    private final CoverageRequirementRepository coverageRequirementRepository;
    private final VenueRepository venueRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;

    public CoverageRequirementService(
            CoverageRequirementRepository coverageRequirementRepository,
            VenueRepository venueRepository,
            ShiftTemplateRepository shiftTemplateRepository) {
        this.coverageRequirementRepository = coverageRequirementRepository;
        this.venueRepository = venueRepository;
        this.shiftTemplateRepository = shiftTemplateRepository;
    }

    @Transactional
    public CoverageRequirementResponse create(CoverageRequirementRequest request) {
        Venue venue = findVenueOrThrow(request.venueId());
        ShiftTemplate shiftTemplate = findShiftTemplateOrThrow(request.venueId(), request.shiftTemplateId());

        coverageRequirementRepository
                .findByVenueIdAndDayOfWeekAndShiftTemplateId(request.venueId(), request.dayOfWeek(), request.shiftTemplateId())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Ya existe un requisito de cobertura para " + request.dayOfWeek() + " y ese turno");
                });

        CoverageRequirement coverageRequirement =
                new CoverageRequirement(venue, request.dayOfWeek(), shiftTemplate, request.requiredCount());
        return toResponse(coverageRequirementRepository.save(coverageRequirement));
    }

    @Transactional(readOnly = true)
    public List<CoverageRequirementResponse> findAll() {
        return coverageRequirementRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CoverageRequirementResponse findById(Long id) {
        return toResponse(findCoverageRequirementOrThrow(id));
    }

    @Transactional
    public CoverageRequirementResponse update(Long id, CoverageRequirementRequest request) {
        CoverageRequirement coverageRequirement = findCoverageRequirementOrThrow(id);
        Venue venue = findVenueOrThrow(request.venueId());
        ShiftTemplate shiftTemplate = findShiftTemplateOrThrow(request.venueId(), request.shiftTemplateId());

        coverageRequirementRepository
                .findByVenueIdAndDayOfWeekAndShiftTemplateId(request.venueId(), request.dayOfWeek(), request.shiftTemplateId())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Ya existe un requisito de cobertura para " + request.dayOfWeek() + " y ese turno");
                });

        coverageRequirement.setVenue(venue);
        coverageRequirement.setDayOfWeek(request.dayOfWeek());
        coverageRequirement.setShiftTemplate(shiftTemplate);
        coverageRequirement.setRequiredCount(request.requiredCount());
        return toResponse(coverageRequirement);
    }

    /** Hard delete: es configuración pura, ningún ShiftAssignment la referencia. */
    @Transactional
    public void delete(Long id) {
        CoverageRequirement coverageRequirement = findCoverageRequirementOrThrow(id);
        coverageRequirementRepository.delete(coverageRequirement);
    }

    private ShiftTemplate findShiftTemplateOrThrow(Long venueId, Long shiftTemplateId) {
        ShiftTemplate shiftTemplate = shiftTemplateRepository.findById(shiftTemplateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ShiftTemplate no encontrado: " + shiftTemplateId));

        if (!shiftTemplate.getVenue().getId().equals(venueId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "El shiftTemplate " + shiftTemplateId + " no pertenece al venue " + venueId);
        }
        if (!shiftTemplate.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "El shiftTemplate " + shiftTemplateId + " está desactivado");
        }
        return shiftTemplate;
    }

    private CoverageRequirement findCoverageRequirementOrThrow(Long id) {
        return coverageRequirementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "CoverageRequirement no encontrado: " + id));
    }

    private Venue findVenueOrThrow(Long venueId) {
        return venueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue no encontrado: " + venueId));
    }

    private CoverageRequirementResponse toResponse(CoverageRequirement coverageRequirement) {
        return new CoverageRequirementResponse(
                coverageRequirement.getId(),
                coverageRequirement.getVenue().getId(),
                coverageRequirement.getDayOfWeek(),
                coverageRequirement.getShiftTemplate().getId(),
                coverageRequirement.getRequiredCount());
    }
}
