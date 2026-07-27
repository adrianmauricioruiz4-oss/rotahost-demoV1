package com.generador.horarios.proyecto.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.employee.EmployeeRole;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Prueba el formulario de login propio (T4.5) contra el flujo real de Spring Security:
 * cookie de sesión + CSRF vía cookie. Cubre en concreto la regresión que se coló al construir
 * esta tarea: sin loginProcessingUrl("/login") explícito, el POST a /login no se reconocía como
 * intento de login y caía en un 401 genérico en vez de autenticar.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class LoginPageTest {

    private static final String PASSWORD = "test1234";
    private static final Pattern CSRF_COOKIE = Pattern.compile("XSRF-TOKEN=([^;]+)");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String employeeEmail;

    @BeforeEach
    void setUp() {
        Venue venue = venueRepository.save(new Venue("Bar Login", LocalTime.of(8, 0), LocalTime.of(2, 0)));
        employeeEmail = "login." + UUID.randomUUID() + "@test.com";
        Employee employee = new Employee("Login Manager", employeeEmail, ContractType.FULL_TIME, null, venue);
        employee.setRole(EmployeeRole.MANAGER);
        employee.setPassword(passwordEncoder.encode(PASSWORD));
        employeeRepository.save(employee);
    }

    @Test
    void loginPageIsReachableWithoutAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/login.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("RotaTeam");
    }

    @Test
    void validCredentialsAuthenticateAndEstablishASession() {
        ResponseEntity<String> loginPage = restTemplate.getForEntity("/login.html", String.class);
        String csrf = extractCsrfCookie(loginPage);
        List<String> pageCookies = loginPage.getHeaders().get(HttpHeaders.SET_COOKIE);

        ResponseEntity<Void> loginAttempt = postLogin(employeeEmail, PASSWORD, csrf, pageCookies);

        assertThat(loginAttempt.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(loginAttempt.getHeaders().getLocation().toString()).endsWith("/dashboard.html");

        HttpHeaders meHeaders = new HttpHeaders();
        meHeaders.add(HttpHeaders.COOKIE, cookieHeader(loginAttempt.getHeaders().get(HttpHeaders.SET_COOKIE)));
        ResponseEntity<String> me = restTemplate.exchange(
                "/api/auth/me", org.springframework.http.HttpMethod.GET, new HttpEntity<>(meHeaders), String.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains(employeeEmail).contains("MANAGER");
    }

    @Test
    void wrongPasswordRedirectsBackToLoginWithError() {
        ResponseEntity<String> loginPage = restTemplate.getForEntity("/login.html", String.class);
        String csrf = extractCsrfCookie(loginPage);
        List<String> pageCookies = loginPage.getHeaders().get(HttpHeaders.SET_COOKIE);

        ResponseEntity<Void> loginAttempt = postLogin(employeeEmail, "wrong-password", csrf, pageCookies);

        assertThat(loginAttempt.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(loginAttempt.getHeaders().getLocation().toString()).contains("/login.html").contains("error");
    }

    private ResponseEntity<Void> postLogin(String username, String password, String csrf, List<String> cookies) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", password);
        form.add("_csrf", csrf);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader(cookies));
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        return restTemplate.withRedirects(HttpRedirects.DONT_FOLLOW).postForEntity("/login", request, Void.class);
    }

    private String cookieHeader(List<String> setCookieHeaders) {
        StringBuilder builder = new StringBuilder();
        for (String header : setCookieHeaders) {
            builder.append(header.split(";", 2)[0]).append("; ");
        }
        return builder.toString();
    }

    private String extractCsrfCookie(ResponseEntity<String> response) {
        for (String header : response.getHeaders().get(HttpHeaders.SET_COOKIE)) {
            Matcher matcher = CSRF_COOKIE.matcher(header);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new IllegalStateException("No se encontró la cookie XSRF-TOKEN en la respuesta de /login.html");
    }
}
