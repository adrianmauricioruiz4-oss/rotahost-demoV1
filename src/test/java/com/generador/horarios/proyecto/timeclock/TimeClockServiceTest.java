package com.generador.horarios.proyecto.timeclock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockEntryResponse;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockStatusResponse;
import com.generador.horarios.proyecto.venue.Venue;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TimeClockServiceTest {

    @Mock
    private TimeClockEntryRepository timeClockEntryRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private TimeClockService timeClockService;
    private Employee employee;

    @BeforeEach
    void setUp() {
        timeClockService = new TimeClockService(timeClockEntryRepository, employeeRepository);
        Venue venue = new Venue("Bar Test", LocalTime.of(8, 0), LocalTime.of(2, 0));
        employee = new Employee("Ana", "ana@test.com", ContractType.FULL_TIME, null, venue);
        ReflectionTestUtils.setField(employee, "id", 1L);
    }

    @Test
    void firstPunchEverIsClockIn() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(timeClockEntryRepository.findTopByEmployeeIdOrderByTimestampDesc(1L)).thenReturn(Optional.empty());
        when(timeClockEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TimeClockEntryResponse response = timeClockService.punch(1L);

        assertThat(response.type()).isEqualTo("CLOCK_IN");
    }

    @Test
    void punchAfterClockInIsClockOut() {
        TimeClockEntry lastEntry = new TimeClockEntry(employee, PunchType.CLOCK_IN, LocalDateTime.now().minusHours(2));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(timeClockEntryRepository.findTopByEmployeeIdOrderByTimestampDesc(1L)).thenReturn(Optional.of(lastEntry));
        when(timeClockEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TimeClockEntryResponse response = timeClockService.punch(1L);

        assertThat(response.type()).isEqualTo("CLOCK_OUT");
    }

    @Test
    void punchAfterClockOutIsClockInAgain() {
        TimeClockEntry lastEntry = new TimeClockEntry(employee, PunchType.CLOCK_OUT, LocalDateTime.now().minusHours(8));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(timeClockEntryRepository.findTopByEmployeeIdOrderByTimestampDesc(1L)).thenReturn(Optional.of(lastEntry));
        when(timeClockEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TimeClockEntryResponse response = timeClockService.punch(1L);

        assertThat(response.type()).isEqualTo("CLOCK_IN");
    }

    @Test
    void statusWithNoPriorPunchesExpectsClockInNext() {
        when(timeClockEntryRepository.findTopByEmployeeIdOrderByTimestampDesc(1L)).thenReturn(Optional.empty());

        TimeClockStatusResponse status = timeClockService.status(1L);

        assertThat(status.nextAction()).isEqualTo("CLOCK_IN");
        assertThat(status.lastEntry()).isNull();
    }
}
