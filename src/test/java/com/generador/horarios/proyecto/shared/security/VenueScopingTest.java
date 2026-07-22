package com.generador.horarios.proyecto.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.employee.EmployeeRole;
import com.generador.horarios.proyecto.employee.dto.EmployeeRequest;
import com.generador.horarios.proyecto.preference.PreferenceType;
import com.generador.horarios.proyecto.preference.dto.PreferenceRequest;
import com.generador.horarios.proyecto.schedule.dto.GenerateScheduleRequest;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import com.generador.horarios.proyecto.venue.dto.VenueRequest;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * T4.2: cada cuenta pertenece a un único venue. Un MANAGER del venue A no debe poder leer ni
 * escribir nada del venue B, aunque conozca sus IDs.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class VenueScopingTest {

    private static final String PASSWORD = "test1234";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Venue venueA;
    private Venue venueB;
    private Employee managerA;
    private Employee employeeB;

    @BeforeEach
    void setUp() {
        venueA = venueRepository.save(new Venue("Bar A", LocalTime.of(8, 0), LocalTime.of(2, 0)));
        venueB = venueRepository.save(new Venue("Bar B", LocalTime.of(8, 0), LocalTime.of(2, 0)));
        managerA = saveEmployee("Encargada A", EmployeeRole.MANAGER, venueA);
        employeeB = saveEmployee("Empleado B", EmployeeRole.EMPLOYEE, venueB);
    }

    private Employee saveEmployee(String name, EmployeeRole role, Venue venue) {
        String email = name.toLowerCase().replace(" ", ".") + "." + UUID.randomUUID() + "@test.com";
        Employee employee = new Employee(name, email, ContractType.FULL_TIME, null, venue);
        employee.setRole(role);
        employee.setPassword(passwordEncoder.encode(PASSWORD));
        return employeeRepository.save(employee);
    }

    private TestRestTemplate asManagerA() {
        return restTemplate.withBasicAuth(managerA.getEmail(), PASSWORD);
    }

    @Test
    void managerCannotReadAnotherVenue() {
        ResponseEntity<String> response = asManagerA().getForEntity("/api/venues/" + venueB.getId(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void managerCannotCreateEmployeeInAnotherVenue() {
        EmployeeRequest request = new EmployeeRequest(
                "Intruso", "intruso." + UUID.randomUUID() + "@test.com", ContractType.FULL_TIME, null, venueB.getId(), null);

        ResponseEntity<String> response = asManagerA().postForEntity("/api/employees", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void managerCannotEditEmployeeOfAnotherVenue() {
        EmployeeRequest request = new EmployeeRequest(
                employeeB.getName(), employeeB.getEmail(), ContractType.FULL_TIME, null, venueB.getId(), null);

        ResponseEntity<String> response = asManagerA().exchange(
                "/api/employees/" + employeeB.getId(), org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void employeeListOnlyReturnsOwnVenue() {
        ResponseEntity<List<Object>> response = asManagerA().exchange(
                "/api/employees", org.springframework.http.HttpMethod.GET, null,
                new org.springframework.core.ParameterizedTypeReference<List<Object>>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void managerCannotUpdateAnotherVenue() {
        VenueRequest request = new VenueRequest("Bar B renombrado", LocalTime.of(9, 0), LocalTime.of(1, 0));

        ResponseEntity<String> response = asManagerA().exchange(
                "/api/venues/" + venueB.getId(), org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(request), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void managerCannotGenerateScheduleForAnotherVenue() {
        GenerateScheduleRequest request = new GenerateScheduleRequest(venueB.getId(), 2026, 30);

        ResponseEntity<String> response = asManagerA().postForEntity("/api/schedules/generate", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void managerCannotTouchPreferencesOfAnotherVenuesEmployee() {
        PreferenceRequest request =
                new PreferenceRequest(employeeB.getId(), PreferenceType.PREFERS_DAY, DayOfWeek.MONDAY, null, null, 3);

        ResponseEntity<String> response = asManagerA().postForEntity("/api/preferences", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
