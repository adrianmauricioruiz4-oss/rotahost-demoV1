package com.generador.horarios.proyecto.shared.security;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.employee.EmployeeRole;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Resuelve el Employee autenticado a partir del Authentication de Spring Security (username = email). */
@Service
public class CurrentUserService {

    private final EmployeeRepository employeeRepository;

    public CurrentUserService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public Employee currentEmployee(Authentication authentication) {
        return employeeRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no reconocido"));
    }

    public boolean isManager(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + EmployeeRole.MANAGER.name()));
    }

    /** Lanza 403 si no es MANAGER y el employeeId objetivo no es el suyo propio. */
    public void requireOwnershipOrManager(Long targetEmployeeId, Authentication authentication) {
        if (isManager(authentication)) {
            return;
        }
        Long ownId = currentEmployee(authentication).getId();
        if (!ownId.equals(targetEmployeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar datos de otro empleado");
        }
    }
}
