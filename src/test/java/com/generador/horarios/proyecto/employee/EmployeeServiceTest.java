package com.generador.horarios.proyecto.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.employee.dto.EmployeeRequest;
import com.generador.horarios.proyecto.employee.dto.EmployeeResponse;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private VenueRepository venueRepository;

    private EmployeeService employeeService;
    private Venue venue;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeService(employeeRepository, venueRepository);
        venue = new Venue("Bar Test", LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(venue, "id", 1L);
    }

    @Test
    void createsFullTimeEmployee() {
        EmployeeRequest request = new EmployeeRequest("Ana", "ana@test.com", ContractType.FULL_TIME, null, 1L);
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(employeeRepository.existsByEmail("ana@test.com")).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        EmployeeResponse response = employeeService.create(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.contractHours()).isNull();
        assertThat(response.active()).isTrue();
        assertThat(response.venueId()).isEqualTo(1L);
    }

    @Test
    void rejectsDuplicateEmailOnCreate() {
        EmployeeRequest request = new EmployeeRequest("Ana", "ana@test.com", ContractType.FULL_TIME, null, 1L);
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(employeeRepository.existsByEmail("ana@test.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ana@test.com");
    }

    @Test
    void rejectsUnknownVenueOnCreate() {
        EmployeeRequest request = new EmployeeRequest("Ana", "ana@test.com", ContractType.FULL_TIME, null, 99L);
        when(venueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void requiresContractHoursForPartTime() {
        EmployeeRequest request = new EmployeeRequest("Ana", "ana@test.com", ContractType.PART_TIME, null, 1L);
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(employeeRepository.existsByEmail("ana@test.com")).thenReturn(false);

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("contractHours");
    }

    @Test
    void rejectsContractHoursForFullTime() {
        EmployeeRequest request = new EmployeeRequest("Ana", "ana@test.com", ContractType.FULL_TIME, 20, 1L);
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(employeeRepository.existsByEmail("ana@test.com")).thenReturn(false);

        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("contractHours");
    }

    @Test
    void allowsKeepingOwnEmailOnUpdate() {
        Employee existing = new Employee("Ana", "ana@test.com", ContractType.FULL_TIME, null, venue);
        ReflectionTestUtils.setField(existing, "id", 10L);
        EmployeeRequest request = new EmployeeRequest("Ana Updated", "ana@test.com", ContractType.FULL_TIME, null, 1L);

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(employeeRepository.findByEmail("ana@test.com")).thenReturn(Optional.of(existing));

        EmployeeResponse response = employeeService.update(10L, request);

        assertThat(response.name()).isEqualTo("Ana Updated");
    }

    @Test
    void rejectsDuplicateEmailOnUpdate() {
        Employee existing = new Employee("Ana", "ana@test.com", ContractType.FULL_TIME, null, venue);
        ReflectionTestUtils.setField(existing, "id", 10L);
        Employee other = new Employee("Bea", "bea@test.com", ContractType.FULL_TIME, null, venue);
        ReflectionTestUtils.setField(other, "id", 20L);

        EmployeeRequest request = new EmployeeRequest("Ana", "bea@test.com", ContractType.FULL_TIME, null, 1L);

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(venueRepository.findById(1L)).thenReturn(Optional.of(venue));
        when(employeeRepository.findByEmail("bea@test.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> employeeService.update(10L, request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void softDeleteDeactivatesEmployeeWithoutRemovingIt() {
        Employee existing = new Employee("Ana", "ana@test.com", ContractType.FULL_TIME, null, venue);
        ReflectionTestUtils.setField(existing, "id", 10L);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(existing));

        employeeService.delete(10L);

        assertThat(existing.isActive()).isFalse();
        verify(employeeRepository, never()).delete(any());
        verify(employeeRepository, never()).deleteById(any());
    }
}
