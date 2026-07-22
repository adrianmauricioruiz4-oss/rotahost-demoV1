package com.generador.horarios.proyecto.preference;

import com.generador.horarios.proyecto.preference.dto.PreferenceRequest;
import com.generador.horarios.proyecto.preference.dto.PreferenceResponse;
import com.generador.horarios.proyecto.shared.security.CurrentUserService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Un EMPLOYEE solo puede leer/escribir sus propias preferencias; un MANAGER no tiene
 * restricción. La comprobación de propiedad vive aquí (no en PreferenceService) porque
 * depende del Authentication de la petición, no de una regla de negocio del dominio.
 */
@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final PreferenceService preferenceService;
    private final CurrentUserService currentUserService;

    public PreferenceController(PreferenceService preferenceService, CurrentUserService currentUserService) {
        this.preferenceService = preferenceService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PreferenceResponse create(@Valid @RequestBody PreferenceRequest request, Authentication authentication) {
        currentUserService.requireOwnershipOrManager(request.employeeId(), authentication);
        return preferenceService.create(request);
    }

    @GetMapping
    public List<PreferenceResponse> findAll(
            @RequestParam(required = false) Long employeeId, Authentication authentication) {
        if (!currentUserService.isManager(authentication)) {
            Long ownId = currentUserService.currentEmployee(authentication).getId();
            if (employeeId != null && !employeeId.equals(ownId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes ver preferencias de otro empleado");
            }
            employeeId = ownId;
        }
        return preferenceService.findAll(employeeId);
    }

    @GetMapping("/{id}")
    public PreferenceResponse findById(@PathVariable Long id, Authentication authentication) {
        PreferenceResponse preference = preferenceService.findById(id);
        currentUserService.requireOwnershipOrManager(preference.employeeId(), authentication);
        return preference;
    }

    @PutMapping("/{id}")
    public PreferenceResponse update(
            @PathVariable Long id, @Valid @RequestBody PreferenceRequest request, Authentication authentication) {
        currentUserService.requireOwnershipOrManager(preferenceService.findById(id).employeeId(), authentication);
        currentUserService.requireOwnershipOrManager(request.employeeId(), authentication);
        return preferenceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        currentUserService.requireOwnershipOrManager(preferenceService.findById(id).employeeId(), authentication);
        preferenceService.delete(id);
    }
}
