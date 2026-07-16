package com.generador.horarios.proyecto.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.dto.EmployeeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void notFoundExceptionReturnsUniformApiError() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity("/api/employees/999999", ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo("Not Found");
        assertThat(body.message()).contains("999999");
        assertThat(body.path()).isEqualTo("/api/employees/999999");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void validationFailureReturnsFieldDetails() {
        EmployeeRequest invalidRequest = new EmployeeRequest("", "no-es-un-email", ContractType.FULL_TIME, null, 1L);

        ResponseEntity<ApiError> response = restTemplate.postForEntity("/api/employees", invalidRequest, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.details()).isNotEmpty();
        assertThat(body.details()).anyMatch(detail -> detail.startsWith("name:"));
        assertThat(body.details()).anyMatch(detail -> detail.startsWith("email:"));
    }
}
