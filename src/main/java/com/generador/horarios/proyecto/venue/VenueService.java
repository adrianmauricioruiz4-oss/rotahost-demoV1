package com.generador.horarios.proyecto.venue;

import com.generador.horarios.proyecto.venue.dto.VenueRequest;
import com.generador.horarios.proyecto.venue.dto.VenueResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lectura y edición del venue (nombre y horario de apertura/cierre). Sin
 * alta/baja: los turnos, coberturas y empleados cuelgan de un venue existente
 * y su ciclo de vida no está resuelto todavía (ver T1.7 en CLAUDE.md).
 */
@Service
public class VenueService {

    private final VenueRepository venueRepository;

    public VenueService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> findAll() {
        return venueRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VenueResponse findById(Long id) {
        return toResponse(findVenueOrThrow(id));
    }

    @Transactional
    public VenueResponse update(Long id, VenueRequest request) {
        Venue venue = findVenueOrThrow(id);
        venue.setName(request.name());
        venue.setOpeningTime(request.openingTime());
        venue.setClosingTime(request.closingTime());
        return toResponse(venue);
    }

    private Venue findVenueOrThrow(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue no encontrado: " + id));
    }

    private VenueResponse toResponse(Venue venue) {
        return new VenueResponse(venue.getId(), venue.getName(), venue.getOpeningTime(), venue.getClosingTime());
    }
}
