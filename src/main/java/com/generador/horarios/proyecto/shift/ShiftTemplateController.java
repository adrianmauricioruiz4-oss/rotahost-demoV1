package com.generador.horarios.proyecto.shift;

import com.generador.horarios.proyecto.shift.dto.ShiftTemplateRequest;
import com.generador.horarios.proyecto.shift.dto.ShiftTemplateResponse;
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
@RequestMapping("/api/shift-templates")
public class ShiftTemplateController {

    private final ShiftTemplateService shiftTemplateService;

    public ShiftTemplateController(ShiftTemplateService shiftTemplateService) {
        this.shiftTemplateService = shiftTemplateService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftTemplateResponse create(@Valid @RequestBody ShiftTemplateRequest request) {
        return shiftTemplateService.create(request);
    }

    @GetMapping
    public List<ShiftTemplateResponse> findAll() {
        return shiftTemplateService.findAll();
    }

    @GetMapping("/{id}")
    public ShiftTemplateResponse findById(@PathVariable Long id) {
        return shiftTemplateService.findById(id);
    }

    @PutMapping("/{id}")
    public ShiftTemplateResponse update(@PathVariable Long id, @Valid @RequestBody ShiftTemplateRequest request) {
        return shiftTemplateService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        shiftTemplateService.delete(id);
    }
}
