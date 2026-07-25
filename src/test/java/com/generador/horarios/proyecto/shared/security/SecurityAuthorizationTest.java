package com.generador.horarios.proyecto.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.employee.EmployeeRole;
import com.generador.horarios.proyecto.employee.dto.EmployeeRequest;
import com.generador.horarios.proyecto.preference.PreferenceType;
import com.generador.horarios.proyecto.preference.dto.PreferenceRequest;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
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
 * Cubre la escalera de autorización de T4.1: sin login -> 401, EMPLOYEE tocando algo de
 * MANAGER o de otro empleado -> 403, dueño o MANAGER -> pasa.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class SecurityAuthorizationTest {

    private static final String PASSWORD = "test1234";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Venue venue;
    private Employee manager;
    private Employee employeeOne;
    private Employee employeeTwo;

    @BeforeEach
    void setUp() {
        venue = venueRepository.save(new Venue("Bar Seguridad", LocalTime.of(8, 0), LocalTime.of(2, 0)));
        manager = saveEmployee("Encargado", EmployeeRole.MANAGER);
        employeeOne = saveEmployee("Empleado Uno", EmployeeRole.EMPLOYEE);
        employeeTwo = saveEmployee("Empleado Dos", EmployeeRole.EMPLOYEE);
    }

    private Employee saveEmployee(String name, EmployeeRole role) {
        String email = name.toLowerCase().replace(" ", ".") + "." + UUID.randomUUID() + "@test.com";
        Employee employee = new Employee(name, email, ContractType.FULL_TIME, null, venue);
        employee.setRole(role);
        employee.setPassword(passwordEncoder.encode(PASSWORD));
        return employeeRepository.save(employee);
    }

    private TestRestTemplate as(Employee employee) {
        return restTemplate.withBasicAuth(employee.getEmail(), PASSWORD);
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/employees", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void employeeCannotCreateEmployees() {
        EmployeeRequest request = new EmployeeRequest(
                "Nuevo", "nuevo." + UUID.randomUUID() + "@test.com", ContractType.FULL_TIME, null, venue.getId(), null, true, true, true, null, null, null);

        ResponseEntity<String> response = as(employeeOne).postForEntity("/api/employees", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void managerCanCreateEmployees() {
        EmployeeRequest request = new EmployeeRequest(
                "Nuevo", "nuevo." + UUID.randomUUID() + "@test.com", ContractType.FULL_TIME, null, venue.getId(), null, true, true, true, null, null, null);

        ResponseEntity<String> response = as(manager).postForEntity("/api/employees", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void employeeCannotCreatePreferenceForSomeoneElse() {
        PreferenceRequest request =
                new PreferenceRequest(employeeTwo.getId(), PreferenceType.PREFERS_DAY, DayOfWeek.MONDAY, null, null, 3);

        ResponseEntity<String> response = as(employeeOne).postForEntity("/api/preferences", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void employeeCanCreateOwnPreference() {
        PreferenceRequest request =
                new PreferenceRequest(employeeOne.getId(), PreferenceType.PREFERS_DAY, DayOfWeek.MONDAY, null, null, 3);

        ResponseEntity<String> response = as(employeeOne).postForEntity("/api/preferences", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void managerCanCreatePreferenceForAnyEmployee() {
        PreferenceRequest request =
                new PreferenceRequest(employeeTwo.getId(), PreferenceType.PREFERS_DAY, DayOfWeek.MONDAY, null, null, 3);

        ResponseEntity<String> response = as(manager).postForEntity("/api/preferences", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
