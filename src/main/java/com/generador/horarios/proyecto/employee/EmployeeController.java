package com.generador.horarios.proyecto.employee;

import com.generador.horarios.proyecto.employee.dto.EmployeeRequest;
import com.generador.horarios.proyecto.employee.dto.EmployeeResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cada cuenta pertenece a un único venue: toda operación queda restringida al propio (T4.2). */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final CurrentUserService currentUserService;

    public EmployeeController(EmployeeService employeeService, CurrentUserService currentUserService) {
        this.employeeService = employeeService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(@Valid @RequestBody EmployeeRequest request, Authentication authentication) {
        currentUserService.requireOwnVenue(request.venueId(), authentication);
        return employeeService.create(request);
    }

    @GetMapping
    public List<EmployeeResponse> findAll(Authentication authentication) {
        Long venueId = currentUserService.currentVenueId(authentication);
        return employeeService.findAll().stream().filter(e -> e.venueId().equals(venueId)).toList();
    }

    @GetMapping("/{id}")
    public EmployeeResponse findById(@PathVariable Long id, Authentication authentication) {
        EmployeeResponse employee = employeeService.findById(id);
        currentUserService.requireOwnVenue(employee.venueId(), authentication);
        return employee;
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(
            @PathVariable Long id, @Valid @RequestBody EmployeeRequest request, Authentication authentication) {
        currentUserService.requireOwnVenue(employeeService.findById(id).venueId(), authentication);
        currentUserService.requireOwnVenue(request.venueId(), authentication);
        return employeeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        currentUserService.requireOwnVenue(employeeService.findById(id).venueId(), authentication);
        employeeService.delete(id);
    }
}
