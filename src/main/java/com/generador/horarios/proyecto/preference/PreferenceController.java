package com.generador.horarios.proyecto.preference;

import com.generador.horarios.proyecto.preference.dto.PreferenceRequest;
import com.generador.horarios.proyecto.preference.dto.PreferenceResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PreferenceResponse create(@Valid @RequestBody PreferenceRequest request) {
        return preferenceService.create(request);
    }

    @GetMapping
    public List<PreferenceResponse> findAll(@RequestParam(required = false) Long employeeId) {
        return preferenceService.findAll(employeeId);
    }

    @GetMapping("/{id}")
    public PreferenceResponse findById(@PathVariable Long id) {
        return preferenceService.findById(id);
    }

    @PutMapping("/{id}")
    public PreferenceResponse update(@PathVariable Long id, @Valid @RequestBody PreferenceRequest request) {
        return preferenceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        preferenceService.delete(id);
    }
}
