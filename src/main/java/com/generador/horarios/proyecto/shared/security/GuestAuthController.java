package com.generador.horarios.proyecto.shared.security;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.employee.EmployeeRole;
import com.generador.horarios.proyecto.shared.security.dto.GuestLoginRequest;
import com.generador.horarios.proyecto.shared.security.dto.GuestRosterEntry;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Acceso "invitado": un trabajador entra eligiendo su nombre de una lista, sin contraseña.
 * Pensado para que alguien del equipo pueda fichar y ver su semana sin tener que memorizar
 * credenciales. Un invitado solo puede ver su propio cuadrante y fichar; SecurityConfig le
 * niega escritura en preferencias/empleados/etc. porque ROLE_GUEST no es ni EMPLOYEE ni MANAGER.
 *
 * <p>No hay entidad ni tabla nueva para esto: reutiliza el Employee ya existente (mismo email
 * como principal), así que CurrentUserService seguiría resolviendo al empleado real sin cambios.
 */
@RestController
@RequestMapping("/api/auth")
public class GuestAuthController {

    private final EmployeeRepository employeeRepository;
    private final VenueRepository venueRepository;
    private final SecurityContextRepository securityContextRepository;

    public GuestAuthController(
            EmployeeRepository employeeRepository,
            VenueRepository venueRepository,
            SecurityContextRepository securityContextRepository) {
        this.employeeRepository = employeeRepository;
        this.venueRepository = venueRepository;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Nombres seleccionables para entrar como invitado: solo id+nombre, sin datos sensibles,
     * y sin requerir sesión (es lo que hace falta ANTES de poder autenticarse). Si no se pasa
     * venueId se usa el primero que haya, porque login.html no elige venue (un único venue por
     * despliegue, igual que el resto de la app); venueId existe sobre todo para poder testear
     * esto sin depender de qué venue haya quedado primero en la base de datos.
     */
    @GetMapping("/guest-roster")
    public List<GuestRosterEntry> guestRoster(@RequestParam(required = false) Long venueId) {
        Long targetVenueId = venueId != null
                ? venueId
                : venueRepository.findAll().stream().findFirst().map(Venue::getId).orElse(null);
        if (targetVenueId == null) {
            return List.of();
        }
        return employeeRepository.findByVenueIdAndActiveTrue(targetVenueId).stream()
                .filter(employee -> employee.getRole() == EmployeeRole.EMPLOYEE)
                .map(employee -> new GuestRosterEntry(employee.getId(), employee.getName()))
                .toList();
    }

    /**
     * Autentica manualmente sin password: construye la Authentication a mano y la persiste en
     * sesión igual que haría el filtro de formLogin, para que el resto de la app (CurrentUserService,
     * /api/auth/me, cualquier controller) no note ninguna diferencia salvo el rol de seguridad.
     */
    @PostMapping("/guest-login")
    public void guestLogin(
            @RequestBody GuestLoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empleado no encontrado"));
        if (employee.getRole() != EmployeeRole.EMPLOYEE || !employee.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ese nombre no está disponible para acceso de invitado");
        }

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_GUEST"));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(employee.getEmail(), null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
    }
}
