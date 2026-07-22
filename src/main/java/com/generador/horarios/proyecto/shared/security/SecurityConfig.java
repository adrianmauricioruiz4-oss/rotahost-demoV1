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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/employees/**", "/api/shift-templates/**", "/api/coverage-requirements/**")
                        .hasRole("MANAGER")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/employees/**", "/api/shift-templates/**", "/api/coverage-requirements/**",
                                "/api/venues/**", "/api/schedules/*/assignments")
                        .hasRole("MANAGER")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/employees/**", "/api/shift-templates/**", "/api/coverage-requirements/**")
                        .hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/schedules/generate", "/api/schedules/*/publish")
                        .hasRole("MANAGER")
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.pathPattern("/api/**")))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
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
