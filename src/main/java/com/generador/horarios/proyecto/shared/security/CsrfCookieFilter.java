package com.generador.horarios.proyecto.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Con CookieCsrfTokenRepository el token es "diferido": la cookie XSRF-TOKEN no se
 * escribe hasta que algo llama a CsrfToken#getToken(). Forzamos esa lectura en cada
 * petición para que el frontend siempre tenga la cookie disponible desde la primera
 * carga de página, sin depender de que algún form la consulte antes.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
