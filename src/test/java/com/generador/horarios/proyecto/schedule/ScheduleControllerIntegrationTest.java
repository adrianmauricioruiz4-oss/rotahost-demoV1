package com.generador.horarios.proyecto.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.schedule.dto.GenerateScheduleRequest;
import com.generador.horarios.proyecto.schedule.dto.ScheduleGenerationResponse;
import com.generador.horarios.proyecto.shift.ShiftSegment;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.shift.ShiftTemplateRepository;
import com.generador.horarios.proyecto.venue.CoverageRequirement;
import com.generador.horarios.proyecto.venue.CoverageRequirementRepository;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueba el cableado real (repositorios con finders derivados + beans de
 * EngineConfig + serialización JSON), complementando ScheduleServiceTest
 * (que mockea todo y cubre los casos de error).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class ScheduleControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ShiftTemplateRepository shiftTemplateRepository;

    @Autowired
    private CoverageRequirementRepository coverageRequirementRepository;

    @Test
    void generatesAndPersistsADraftScheduleThroughTheRealStack() {
        Venue venue = venueRepository.save(new Venue("Bar Integración", LocalTime.of(8, 0), LocalTime.of(2, 0)));
        ShiftTemplate manana = shiftTemplateRepository.save(new ShiftTemplate("MAÑANA", venue,
                List.of(new ShiftSegment(LocalTime.of(8, 0), LocalTime.of(16, 0)))));
        employeeRepository.save(new Employee("Ana", "ana.integracion@test.com", ContractType.FULL_TIME, null, venue));
        coverageRequirementRepository.save(new CoverageRequirement(venue, DayOfWeek.MONDAY, manana, 1));

        GenerateScheduleRequest request = new GenerateScheduleRequest(venue.getId(), 2026, 30);

        ResponseEntity<ScheduleGenerationResponse> response =
                restTemplate.postForEntity("/api/schedules/generate", request, ScheduleGenerationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ScheduleGenerationResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.scheduleId()).isNotNull();
        assertThat(body.status()).isEqualTo("DRAFT");
        assertThat(body.assignments()).hasSize(1);
        assertThat(body.uncoveredSlots()).isEmpty();
    }
}
