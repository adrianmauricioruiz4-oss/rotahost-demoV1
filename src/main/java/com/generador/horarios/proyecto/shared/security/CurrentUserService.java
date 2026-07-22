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

    /** Cada cuenta pertenece a un único venue (el de su Employee); no hay cuentas multi-venue. */
    public Long currentVenueId(Authentication authentication) {
        return currentEmployee(authentication).getVenue().getId();
    }

    /** Lanza 403 si el venue objetivo no es el propio. Se aplica a MANAGER y EMPLOYEE por igual. */
    public void requireOwnVenue(Long targetVenueId, Authentication authentication) {
        if (!currentVenueId(authentication).equals(targetVenueId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a datos de otro venue");
        }
    }

    /**
     * Lanza 403 salvo que: (a) el objetivo seas tú mismo, o (b) seas MANAGER del mismo venue que
     * el empleado objetivo. Un MANAGER de un venue no puede tocar empleados de otro venue.
     */
    public void requireOwnershipOrManager(Long targetEmployeeId, Authentication authentication) {
        Employee current = currentEmployee(authentication);
        if (current.getId().equals(targetEmployeeId)) {
            return;
        }
        if (isManager(authentication)) {
            Employee target = employeeRepository.findById(targetEmployeeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Employee no encontrado: " + targetEmployeeId));
            if (current.getVenue().getId().equals(target.getVenue().getId())) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes modificar datos de otro empleado");
    }
}
