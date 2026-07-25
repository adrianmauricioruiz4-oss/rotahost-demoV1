package com.generador.horarios.proyecto.venue;

import com.generador.horarios.proyecto.shared.security.CurrentUserService;
import com.generador.horarios.proyecto.venue.dto.VenueRequest;
import com.generador.horarios.proyecto.venue.dto.VenueResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cada cuenta pertenece a un único venue: toda operación queda restringida al propio (T4.2). */
@RestController
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueService venueService;
    private final CurrentUserService currentUserService;

    public VenueController(VenueService venueService, CurrentUserService currentUserService) {
        this.venueService = venueService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<VenueResponse> findAll(Authentication authentication) {
        Long venueId = currentUserService.currentVenueId(authentication);
        return venueService.findAll().stream().filter(v -> v.id().equals(venueId)).toList();
    }

    @GetMapping("/{id}")
    public VenueResponse findById(@PathVariable Long id, Authentication authentication) {
        currentUserService.requireOwnVenue(id, authentication);
        return venueService.findById(id);
    }

    @PutMapping("/{id}")
    public VenueResponse update(@PathVariable Long id, @Valid @RequestBody VenueRequest request, Authentication authentication) {
        currentUserService.requireOwnVenue(id, authentication);
        return venueService.update(id, request);
    }
}
