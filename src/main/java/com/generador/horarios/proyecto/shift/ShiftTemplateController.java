package com.generador.horarios.proyecto.shift;

import com.generador.horarios.proyecto.shared.security.CurrentUserService;
import com.generador.horarios.proyecto.shift.dto.ShiftTemplateRequest;
import com.generador.horarios.proyecto.shift.dto.ShiftTemplateResponse;
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
@RequestMapping("/api/shift-templates")
public class ShiftTemplateController {

    private final ShiftTemplateService shiftTemplateService;
    private final CurrentUserService currentUserService;

    public ShiftTemplateController(ShiftTemplateService shiftTemplateService, CurrentUserService currentUserService) {
        this.shiftTemplateService = shiftTemplateService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftTemplateResponse create(@Valid @RequestBody ShiftTemplateRequest request, Authentication authentication) {
        currentUserService.requireOwnVenue(request.venueId(), authentication);
        return shiftTemplateService.create(request);
    }

    @GetMapping
    public List<ShiftTemplateResponse> findAll(Authentication authentication) {
        Long venueId = currentUserService.currentVenueId(authentication);
        return shiftTemplateService.findAll().stream().filter(t -> t.venueId().equals(venueId)).toList();
    }

    @GetMapping("/{id}")
    public ShiftTemplateResponse findById(@PathVariable Long id, Authentication authentication) {
        ShiftTemplateResponse shiftTemplate = shiftTemplateService.findById(id);
        currentUserService.requireOwnVenue(shiftTemplate.venueId(), authentication);
        return shiftTemplate;
    }

    @PutMapping("/{id}")
    public ShiftTemplateResponse update(
            @PathVariable Long id, @Valid @RequestBody ShiftTemplateRequest request, Authentication authentication) {
        currentUserService.requireOwnVenue(shiftTemplateService.findById(id).venueId(), authentication);
        currentUserService.requireOwnVenue(request.venueId(), authentication);
        return shiftTemplateService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        currentUserService.requireOwnVenue(shiftTemplateService.findById(id).venueId(), authentication);
        shiftTemplateService.delete(id);
    }
}
