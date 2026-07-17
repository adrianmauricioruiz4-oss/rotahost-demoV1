package com.generador.horarios.proyecto.venue;

import com.generador.horarios.proyecto.venue.dto.VenueRequest;
import com.generador.horarios.proyecto.venue.dto.VenueResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @GetMapping
    public List<VenueResponse> findAll() {
        return venueService.findAll();
    }

    @GetMapping("/{id}")
    public VenueResponse findById(@PathVariable Long id) {
        return venueService.findById(id);
    }

    @PutMapping("/{id}")
    public VenueResponse update(@PathVariable Long id, @Valid @RequestBody VenueRequest request) {
        return venueService.update(id, request);
    }
}
