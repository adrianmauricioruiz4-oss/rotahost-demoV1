package com.generador.horarios.proyecto.venue;

import com.generador.horarios.proyecto.venue.dto.CoverageRequirementRequest;
import com.generador.horarios.proyecto.venue.dto.CoverageRequirementResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coverage-requirements")
public class CoverageRequirementController {

    private final CoverageRequirementService coverageRequirementService;

    public CoverageRequirementController(CoverageRequirementService coverageRequirementService) {
        this.coverageRequirementService = coverageRequirementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CoverageRequirementResponse create(@Valid @RequestBody CoverageRequirementRequest request) {
        return coverageRequirementService.create(request);
    }

    @GetMapping
    public List<CoverageRequirementResponse> findAll() {
        return coverageRequirementService.findAll();
    }

    @GetMapping("/{id}")
    public CoverageRequirementResponse findById(@PathVariable Long id) {
        return coverageRequirementService.findById(id);
    }

    @PutMapping("/{id}")
    public CoverageRequirementResponse update(@PathVariable Long id, @Valid @RequestBody CoverageRequirementRequest request) {
        return coverageRequirementService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        coverageRequirementService.delete(id);
    }
}
