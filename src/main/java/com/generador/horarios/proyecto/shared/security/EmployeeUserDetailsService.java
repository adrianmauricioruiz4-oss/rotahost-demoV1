package com.generador.horarios.proyecto.shared.security;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** El email del Employee hace de username; solo empleados activos con contraseña asignada pueden autenticarse. */
@Service
public class EmployeeUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;

    public EmployeeUserDetailsService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Employee employee = employeeRepository.findByEmail(email)
                .filter(Employee::isActive)
                .orElseThrow(() -> new UsernameNotFoundException("No existe un usuario activo con email " + email));
        if (employee.getPassword() == null) {
            throw new UsernameNotFoundException("El empleado " + email + " no tiene credenciales configuradas");
        }
        return User.withUsername(employee.getEmail())
                .password(employee.getPassword())
                .authorities("ROLE_" + employee.getRole().name())
                .build();
    }
}
