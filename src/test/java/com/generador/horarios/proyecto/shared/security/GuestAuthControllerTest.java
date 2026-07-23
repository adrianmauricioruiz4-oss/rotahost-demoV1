package com.generador.horarios.proyecto.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.employee.EmployeeRole;
import com.generador.horarios.proyecto.shared.security.dto.GuestLoginRequest;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/** Cubre el login de invitado (sin contraseña, por nombre): roster, sesión, y que no cuele un MANAGER. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class GuestAuthControllerTest {

    private static final Pattern CSRF_COOKIE = Pattern.compile("XSRF-TOKEN=([^;]+)");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Venue venue;
    private Employee worker;
    private Employee manager;

    @BeforeEach
    void setUp() {
        venue = venueRepository.save(new Venue("Bar Invitados", LocalTime.of(8, 0), LocalTime.of(2, 0)));
        worker = saveEmployee(venue, "Trabajador Invitado", EmployeeRole.EMPLOYEE);
        manager = saveEmployee(venue, "Encargada Invitados", EmployeeRole.MANAGER);
    }

    private Employee saveEmployee(Venue venue, String name, EmployeeRole role) {
        String email = name.toLowerCase().replace(" ", ".") + "." + UUID.randomUUID() + "@test.com";
        Employee employee = new Employee(name, email, ContractType.FULL_TIME, null, venue);
        employee.setRole(role);
        employee.setPassword(passwordEncoder.encode("whatever1234"));
        return employeeRepository.save(employee);
    }

    @Test
    void rosterListsActiveEmployeesButNotManagers() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/auth/guest-roster?venueId=" + venue.getId(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(worker.getName());
        assertThat(response.getBody()).doesNotContain(manager.getName());
    }

    @Test
    void guestLoginEstablishesASessionAsThatEmployee() {
        ResponseEntity<String> loginPage = restTemplate.getForEntity("/login.html", String.class);
        String csrf = extractCsrfCookie(loginPage);
        List<String> pageCookies = loginPage.getHeaders().get(HttpHeaders.SET_COOKIE);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader(pageCookies));
        headers.add("X-XSRF-TOKEN", csrf);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<GuestLoginRequest> request = new HttpEntity<>(new GuestLoginRequest(worker.getId()), headers);

        ResponseEntity<Void> loginResponse = restTemplate.postForEntity("/api/auth/guest-login", request, Void.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders meHeaders = new HttpHeaders();
        meHeaders.add(HttpHeaders.COOKIE, cookieHeader(loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE)));
        ResponseEntity<String> me = restTemplate.exchange(
                "/api/auth/me", HttpMethod.GET, new HttpEntity<>(meHeaders), String.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains(worker.getEmail());
        assertThat(me.getBody()).contains("\"guest\":true");
    }

    @Test
    void guestLoginRejectsAManagerId() {
        ResponseEntity<String> loginPage = restTemplate.getForEntity("/login.html", String.class);
        String csrf = extractCsrfCookie(loginPage);
        List<String> pageCookies = loginPage.getHeaders().get(HttpHeaders.SET_COOKIE);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader(pageCookies));
        headers.add("X-XSRF-TOKEN", csrf);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<GuestLoginRequest> request = new HttpEntity<>(new GuestLoginRequest(manager.getId()), headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/guest-login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
