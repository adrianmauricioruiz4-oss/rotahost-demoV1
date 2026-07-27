package com.generador.horarios.proyecto.timeclock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockEntryResponse;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockStatusResponse;
import com.generador.horarios.proyecto.venue.Venue;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TimeClockServiceTest {

    /** Lunes, para que la semana ISO empiece justo ahí y el acumulado semanal sea el del día. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 7, 27);

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
        venue.setBreakAllowanceMinutes(15);
        employee = new Employee("Ana", "ana@test.com", ContractType.FULL_TIME, null, venue);
        ReflectionTestUtils.setField(employee, "id", 1L);
    }

    private void lastPunchWas(PunchType type) {
        TimeClockEntry entry = type == null ? null : new TimeClockEntry(employee, type, MONDAY.atTime(10, 0));
        when(timeClockEntryRepository.findTopByEmployeeIdOrderByTimestampDesc(1L))
                .thenReturn(Optional.ofNullable(entry));
    }

    private void savesWhatItIsGiven() {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(timeClockEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void dayHasEntries(List<TimeClockEntry> entries) {
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        lenient().when(timeClockEntryRepository.findByEmployeeIdAndTimestampBetweenOrderByTimestampAsc(
                eq(1L), any(), any())).thenReturn(entries);
    }

    private TimeClockEntry entry(PunchType type, int hour, int minute) {
        return new TimeClockEntry(employee, type, MONDAY.atTime(hour, minute));
    }

    /* ---------- qué acción toca ---------- */

    @Test
    void firstPunchEverIsClockIn() {
        lastPunchWas(null);
        savesWhatItIsGiven();

        TimeClockEntryResponse response = timeClockService.punch(1L);

        assertThat(response.type()).isEqualTo("CLOCK_IN");
    }

    @Test
    void punchAfterClockInIsClockOut() {
        lastPunchWas(PunchType.CLOCK_IN);
        savesWhatItIsGiven();

        assertThat(timeClockService.punch(1L).type()).isEqualTo("CLOCK_OUT");
    }

    @Test
    void punchAfterClockOutIsClockInAgain() {
        lastPunchWas(PunchType.CLOCK_OUT);
        savesWhatItIsGiven();

        assertThat(timeClockService.punch(1L).type()).isEqualTo("CLOCK_IN");
    }

    @Test
    void punchAfterStartingABreakEndsTheBreak() {
        lastPunchWas(PunchType.BREAK_START);
        savesWhatItIsGiven();

        assertThat(timeClockService.punch(1L).type()).isEqualTo("BREAK_END");
    }

    @Test
    void aBreakCanBeStartedExplicitlyWhileWorking() {
        lastPunchWas(PunchType.CLOCK_IN);
        savesWhatItIsGiven();

        assertThat(timeClockService.punch(1L, PunchType.BREAK_START).type()).isEqualTo("BREAK_START");
    }

    @Test
    void clockingOutIsRefusedWhileOnABreak() {
        lastPunchWas(PunchType.BREAK_START);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> timeClockService.punch(1L, PunchType.CLOCK_OUT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("primero termina la pausa");
    }

    @Test
    void aBreakCannotStartBeforeClockingIn() {
        lastPunchWas(null);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> timeClockService.punch(1L, PunchType.BREAK_START))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No has fichado la entrada");
    }

    /* ---------- estado y acumulados ---------- */

    @Test
    void statusWithNoPriorPunchesExpectsClockInNext() {
        lastPunchWas(null);
        dayHasEntries(List.of());

        TimeClockStatusResponse status = timeClockService.status(1L, MONDAY.atTime(9, 0));

        assertThat(status.state()).isEqualTo("OFF");
        assertThat(status.nextAction()).isEqualTo("CLOCK_IN");
        assertThat(status.canStartBreak()).isFalse();
        assertThat(status.lastEntry()).isNull();
        assertThat(status.since()).isNull();
        assertThat(status.workedTodayMinutes()).isZero();
    }

    @Test
    void statusWhileWorkingCountsTheTimeSinceClockingIn() {
        LocalDateTime now = MONDAY.atTime(16, 47);
        lastPunchWas(PunchType.CLOCK_IN);
        dayHasEntries(List.of(entry(PunchType.CLOCK_IN, 12, 4)));

        TimeClockStatusResponse status = timeClockService.status(1L, now);

        assertThat(status.state()).isEqualTo("WORKING");
        assertThat(status.nextAction()).isEqualTo("CLOCK_OUT");
        assertThat(status.canStartBreak()).isTrue();
        assertThat(status.workedTodayMinutes()).isEqualTo(4 * 60 + 43);
        assertThat(status.workedThisWeekMinutes()).isEqualTo(4 * 60 + 43);
        assertThat(status.breakAllowanceMinutes()).isEqualTo(15);
    }

    @Test
    void statusOnABreakOffersOnlyGoingBackToWork() {
        lastPunchWas(PunchType.BREAK_START);
        dayHasEntries(List.of(
                entry(PunchType.CLOCK_IN, 12, 0),
                entry(PunchType.BREAK_START, 18, 29)));

        TimeClockStatusResponse status = timeClockService.status(1L, MONDAY.atTime(18, 47));

        assertThat(status.state()).isEqualTo("ON_BREAK");
        assertThat(status.nextAction()).isEqualTo("BREAK_END");
        assertThat(status.canStartBreak()).isFalse();
        assertThat(status.breakTodayMinutes()).isEqualTo(18);
    }

    @Test
    void aBreakWithinTheAllowanceDoesNotShortenTheWorkedTotal() {
        lastPunchWas(PunchType.CLOCK_OUT);
        dayHasEntries(List.of(
                entry(PunchType.CLOCK_IN, 8, 0),
                entry(PunchType.BREAK_START, 11, 0),
                entry(PunchType.BREAK_END, 11, 15),
                entry(PunchType.CLOCK_OUT, 16, 0)));

        TimeClockStatusResponse status = timeClockService.status(1L, MONDAY.atTime(20, 0));

        assertThat(status.workedTodayMinutes()).isEqualTo(8 * 60);
        assertThat(status.breakTodayMinutes()).isEqualTo(15);
    }

    @Test
    void aBreakLongerThanTheAllowanceOnlyLosesTheExcess() {
        lastPunchWas(PunchType.CLOCK_OUT);
        dayHasEntries(List.of(
                entry(PunchType.CLOCK_IN, 8, 0),
                entry(PunchType.BREAK_START, 11, 0),
                entry(PunchType.BREAK_END, 12, 0),
                entry(PunchType.CLOCK_OUT, 16, 0)));

        TimeClockStatusResponse status = timeClockService.status(1L, MONDAY.atTime(20, 0));

        assertThat(status.workedTodayMinutes()).isEqualTo(8 * 60 - 45);
        assertThat(status.breakTodayMinutes()).isEqualTo(60);
    }
}
