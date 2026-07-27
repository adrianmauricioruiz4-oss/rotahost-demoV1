package com.generador.horarios.proyecto.shared.security;

import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Sesión con cookie para el frontend (formLogin) + Basic auth habilitado para scripts/tests, ya
 * que ninguno de los dos convive mal: Basic no usa credenciales ambiente de navegador, así que
 * queda exento de CSRF (ver requireCsrfProtectionMatcher). El CSRF token viaja en una cookie
 * legible por JS (no HttpOnly) para que el frontend la reenvíe como cabecera X-XSRF-TOKEN.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Igual que el matcher por defecto de CsrfFilter: GET/HEAD/TRACE/OPTIONS nunca mutan estado. */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "TRACE", "OPTIONS");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Compartido entre el filtro de seguridad y GuestAuthController, que la usa para persistir a
     * mano la Authentication del invitado (no pasa por el filtro de formLogin). Reconstruye a
     * propósito la combinación que Spring Security usa por defecto cuando no se personaliza
     * securityContext() (RequestAttribute + HttpSession) en vez de solo HttpSession: usar solo
     * HttpSession aquí rompía SecurityAuthorizationTest (un EMPLOYEE colaba una request como si
     * fuera el MANAGER autenticado justo antes en la misma suite, vía TestRestTemplate compartido).
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityContext(securityContext -> securityContext.securityContextRepository(securityContextRepository()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login.html", "/css/**", "/js/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/guest-roster").permitAll()
                        // La consola enseña las horas de toda la plantilla: solo el encargado.
                        .requestMatchers(HttpMethod.GET, "/api/timeclock/overview").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/auth/guest-login").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/employees/**", "/api/shift-templates/**", "/api/coverage-requirements/**")
                        .hasRole("MANAGER")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/employees/**", "/api/shift-templates/**", "/api/coverage-requirements/**",
                                "/api/venues/**", "/api/schedules/*/assignments", "/api/timeclock/entries/**")
                        .hasRole("MANAGER")
                        // Corregir o anotar el fichaje de otro es cosa del encargado. Fichar el
                        // propio (POST /api/timeclock/punch) sigue abierto a cualquier sesión.
                        .requestMatchers(HttpMethod.POST, "/api/timeclock/entries").hasRole("MANAGER")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/employees/**", "/api/shift-templates/**", "/api/coverage-requirements/**")
                        .hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/schedules/generate", "/api/schedules/*/publish")
                        .hasRole("MANAGER")
                        // Un invitado (ROLE_GUEST) no es ni EMPLOYEE ni MANAGER: puede ver su semana y
                        // fichar, pero no crear/borrar preferencias en nombre de nadie.
                        .requestMatchers(HttpMethod.POST, "/api/preferences/**").hasAnyRole("EMPLOYEE", "MANAGER")
                        .requestMatchers(HttpMethod.DELETE, "/api/preferences/**").hasAnyRole("EMPLOYEE", "MANAGER")
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login.html").loginProcessingUrl("/login")
                        .defaultSuccessUrl("/dashboard.html").permitAll())
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.pathPattern("/api/**")))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login.html?logout"))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(request -> {
                            boolean isMutatingMethod = !SAFE_METHODS.contains(request.getMethod());
                            return isMutatingMethod && request.getHeader("Authorization") == null;
                        }))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }
}
