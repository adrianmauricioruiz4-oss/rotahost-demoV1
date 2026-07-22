package com.generador.horarios.proyecto.venue;

import com.generador.horarios.proyecto.shared.security.CurrentUserService;
import com.generador.horarios.proyecto.venue.dto.CoverageRequirementRequest;
import com.generador.horarios.proyecto.venue.dto.CoverageRequirementResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cada cuenta pertenece a un único venue: toda operación queda restringida al propio (T4.2). */
@RestController
@RequestMapping("/api/coverage-requirements")
public class CoverageRequirementController {

    private final CoverageRequirementService coverageRequirementService;
    private final CurrentUserService currentUserService;

    public CoverageRequirementController(
            CoverageRequirementService coverageRequirementService, CurrentUserService currentUserService) {
        this.coverageRequirementService = coverageRequirementService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CoverageRequirementResponse create(
            @Valid @RequestBody CoverageRequirementRequest request, Authentication authentication) {
        currentUserService.requireOwnVenue(request.venueId(), authentication);
        return coverageRequirementService.create(request);
    }

    @GetMapping
    public List<CoverageRequirementResponse> findAll(Authentication authentication) {
        Long venueId = currentUserService.currentVenueId(authentication);
        return coverageRequirementService.findAll().stream().filter(c -> c.venueId().equals(venueId)).toList();
    }

    @GetMapping("/{id}")
    public CoverageRequirementResponse findById(@PathVariable Long id, Authentication authentication) {
        CoverageRequirementResponse coverageRequirement = coverageRequirementService.findById(id);
        currentUserService.requireOwnVenue(coverageRequirement.venueId(), authentication);
        return coverageRequirement;
    }

    @PutMapping("/{id}")
    public CoverageRequirementResponse update(
            @PathVariable Long id, @Valid @RequestBody CoverageRequirementRequest request, Authentication authentication) {
        currentUserService.requireOwnVenue(coverageRequirementService.findById(id).venueId(), authentication);
        currentUserService.requireOwnVenue(request.venueId(), authentication);
        return coverageRequirementService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        currentUserService.requireOwnVenue(coverageRequirementService.findById(id).venueId(), authentication);
        coverageRequirementService.delete(id);
    }
}
