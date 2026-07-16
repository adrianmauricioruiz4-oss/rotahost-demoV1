package com.generador.horarios.proyecto.shift;

import com.generador.horarios.proyecto.shift.dto.ShiftSegmentRequest;
import com.generador.horarios.proyecto.shift.dto.ShiftSegmentResponse;
import com.generador.horarios.proyecto.shift.dto.ShiftTemplateRequest;
import com.generador.horarios.proyecto.shift.dto.ShiftTemplateResponse;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lógica de negocio del CRUD de ShiftTemplate: valida venue existente y que
 * los segmentos horarios sean coherentes (orden y ausencia de solapes).
 */
@Service
public class ShiftTemplateService {

    private final ShiftTemplateRepository shiftTemplateRepository;
    private final VenueRepository venueRepository;

    public ShiftTemplateService(ShiftTemplateRepository shiftTemplateRepository, VenueRepository venueRepository) {
        this.shiftTemplateRepository = shiftTemplateRepository;
        this.venueRepository = venueRepository;
    }

    @Transactional
    public ShiftTemplateResponse create(ShiftTemplateRequest request) {
        Venue venue = findVenueOrThrow(request.venueId());
        List<ShiftSegment> segments = toValidatedSegments(request.segments());

        ShiftTemplate shiftTemplate = new ShiftTemplate(request.name(), venue, segments);
        return toResponse(shiftTemplateRepository.save(shiftTemplate));
    }

    @Transactional(readOnly = true)
    public List<ShiftTemplateResponse> findAll() {
        return shiftTemplateRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ShiftTemplateResponse findById(Long id) {
        return toResponse(findShiftTemplateOrThrow(id));
    }

    @Transactional
    public ShiftTemplateResponse update(Long id, ShiftTemplateRequest request) {
        ShiftTemplate shiftTemplate = findShiftTemplateOrThrow(id);
        Venue venue = findVenueOrThrow(request.venueId());
        List<ShiftSegment> segments = toValidatedSegments(request.segments());

        shiftTemplate.setName(request.name());
        shiftTemplate.setVenue(venue);
        shiftTemplate.setSegments(segments);
        return toResponse(shiftTemplate);
    }

    /** Soft delete: preserva el histórico para ShiftAssignment ya generados (T2.1). */
    @Transactional
    public void delete(Long id) {
        ShiftTemplate shiftTemplate = findShiftTemplateOrThrow(id);
        shiftTemplate.setActive(false);
    }

    /**
     * endTime puede ser 00:00 (turno que cruza medianoche, ej. TARDE 16:00-00:00);
     * en ese caso se normaliza a 24:00 solo para comparar solapes entre segmentos.
     */
    private List<ShiftSegment> toValidatedSegments(List<ShiftSegmentRequest> requestSegments) {
        List<ShiftSegment> segments = new ArrayList<>();
        List<int[]> minuteRanges = new ArrayList<>();

        for (ShiftSegmentRequest segmentRequest : requestSegments) {
            LocalTime start = segmentRequest.startTime();
            LocalTime end = segmentRequest.endTime();

            if (start.equals(end)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "startTime y endTime no pueden ser iguales: " + start);
            }

            boolean crossesMidnight = end.equals(LocalTime.MIDNIGHT);
            if (!crossesMidnight && end.isBefore(start)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "endTime debe ser posterior a startTime (o 00:00 si el turno cruza la medianoche): "
                                + start + "-" + end);
            }

            int startMinutes = start.toSecondOfDay() / 60;
            int endMinutes = crossesMidnight ? 24 * 60 : end.toSecondOfDay() / 60;

            for (int[] existingRange : minuteRanges) {
                if (startMinutes < existingRange[1] && existingRange[0] < endMinutes) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                            "Los segmentos del turno se solapan entre sí: " + start + "-" + end);
                }
            }

            minuteRanges.add(new int[] {startMinutes, endMinutes});
            segments.add(new ShiftSegment(start, end));
        }
        return segments;
    }

    private ShiftTemplate findShiftTemplateOrThrow(Long id) {
        return shiftTemplateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ShiftTemplate no encontrado: " + id));
    }

    private Venue findVenueOrThrow(Long venueId) {
        return venueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue no encontrado: " + venueId));
    }

    private ShiftTemplateResponse toResponse(ShiftTemplate shiftTemplate) {
        List<ShiftSegmentResponse> segments = shiftTemplate.getSegments().stream()
                .map(segment -> new ShiftSegmentResponse(segment.getStartTime(), segment.getEndTime()))
                .toList();
        return new ShiftTemplateResponse(
                shiftTemplate.getId(),
                shiftTemplate.getName(),
                shiftTemplate.getVenue().getId(),
                segments,
                shiftTemplate.isActive());
    }
}
