package com.generador.horarios.proyecto.shared.security;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.shared.security.dto.AuthMeResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Permite al frontend saber quién está autenticado y con qué rol, sin exponer la contraseña. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUserService currentUserService;

    public AuthController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    public AuthMeResponse me(Authentication authentication) {
        Employee employee = currentUserService.currentEmployee(authentication);
        return new AuthMeResponse(
                employee.getId(), employee.getName(), employee.getEmail(), employee.getRole(), employee.getVenue().getId());
    }
}
