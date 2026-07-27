package com.generador.horarios.proyecto.timeclock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.employee.EmployeeRole;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockCorrectionRequest;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockEntryCreateRequest;
import com.generador.horarios.proyecto.timeclock.dto.TimeClockEntryResponse;
import com.generador.horarios.proyecto.venue.Venue;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Corrección de fichajes por el encargado. Lo que se prueba aquí es que el registro original
 * nunca se pierde y que un encargado no llega a los fichajes de otro local: son las dos cosas
 * que importan si alguien pregunta por qué un fichaje dice lo que dice.
 */
@ExtendWith(MockitoExtension.class)
class TimeClockCorrectionTest {

    private static final LocalDateTime PUNCHED_AT = LocalDateTime.of(2026, 7, 27, 22, 0);
    private static final LocalDateTime CORRECTED_TO = LocalDateTime.of(2026, 7, 27, 23, 30);

    @Mock
    private TimeClockEntryRepository timeClockEntryRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private TimeClockService timeClockService;
    private Venue venue;
    private Employee worker;
    private Employee manager;

    @BeforeEach
    void setUp() {
        timeClockService = new TimeClockService(timeClockEntryRepository, employeeRepository);
        venue = venueWithId(1L, "Restaurante El Mirador");
        worker = employeeWithId(10L, "Jorge Pardo", venue, EmployeeRole.EMPLOYEE);
        manager = employeeWithId(11L, "Ana García", venue, EmployeeRole.MANAGER);
    }

    private Venue venueWithId(Long id, String name) {
        Venue created = new Venue(name, LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    private Employee employeeWithId(Long id, String name, Venue at, EmployeeRole role) {
        Employee created = new Employee(name, name.replace(" ", ".") + "@test.com", ContractType.FULL_TIME, null, at);
        created.setRole(role);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    private TimeClockEntry existingEntry() {
        TimeClockEntry entry = new TimeClockEntry(worker, PunchType.CLOCK_OUT, PUNCHED_AT);
        ReflectionTestUtils.setField(entry, "id", 99L);
        return entry;
    }

    @Test
    void correctingKeepsTheOriginalTimeAndSaysWhoChangedItAndWhy() {
        TimeClockEntry entry = existingEntry();
        when(timeClockEntryRepository.findById(99L)).thenReturn(Optional.of(entry));

        TimeClockEntryResponse response = timeClockService.correct(
                99L, new TimeClockCorrectionRequest(CORRECTED_TO, "Olvidó fichar la salida"), manager);

        assertThat(response.timestamp()).isEqualTo(CORRECTED_TO);
        assertThat(response.originalTimestamp()).isEqualTo(PUNCHED_AT);
        assertThat(response.correctedByName()).isEqualTo("Ana García");
        assertThat(response.correctionReason()).isEqualTo("Olvidó fichar la salida");
        assertThat(response.correctedAt()).isNotNull();
    }

    @Test
    void correctingTwiceStillRemembersWhatTheEmployeeActuallyPunched() {
        TimeClockEntry entry = existingEntry();
        when(timeClockEntryRepository.findById(99L)).thenReturn(Optional.of(entry));

        timeClockService.correct(99L, new TimeClockCorrectionRequest(CORRECTED_TO, "Primer intento"), manager);
        TimeClockEntryResponse second = timeClockService.correct(
                99L, new TimeClockCorrectionRequest(CORRECTED_TO.plusMinutes(15), "Me equivoqué antes"), manager);

        assertThat(second.timestamp()).isEqualTo(CORRECTED_TO.plusMinutes(15));
        assertThat(second.originalTimestamp()).isEqualTo(PUNCHED_AT);
        assertThat(second.correctionReason()).isEqualTo("Me equivoqué antes");
    }

    @Test
    void aManagerCannotTouchAPunchFromAnotherVenue() {
        Employee otherVenueManager = employeeWithId(20L, "Otro Encargado", venueWithId(2L, "Bar B"), EmployeeRole.MANAGER);
        when(timeClockEntryRepository.findById(99L)).thenReturn(Optional.of(existingEntry()));

        assertThatThrownBy(() -> timeClockService.correct(
                99L, new TimeClockCorrectionRequest(CORRECTED_TO, "No es mío"), otherVenueManager))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void correctingAPunchThatDoesNotExistIsANotFound() {
        when(timeClockEntryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timeClockService.correct(
                404L, new TimeClockCorrectionRequest(CORRECTED_TO, "Da igual"), manager))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aManagerCanCloseAShiftThatWasLeftOpen() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(worker));
        when(timeClockEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TimeClockEntryResponse response = timeClockService.addOnBehalf(
                new TimeClockEntryCreateRequest(10L, "CLOCK_OUT", CORRECTED_TO, "Se fue sin fichar"), manager);

        assertThat(response.type()).isEqualTo("CLOCK_OUT");
        assertThat(response.timestamp()).isEqualTo(CORRECTED_TO);
        assertThat(response.addedByManager()).isTrue();
        assertThat(response.correctedByName()).isEqualTo("Ana García");
        assertThat(response.correctionReason()).isEqualTo("Se fue sin fichar");
        // No nació de una corrección: no hay hora anterior que fingir.
        assertThat(response.originalTimestamp()).isNull();
    }

    @Test
    void aManagerCannotAddAPunchForSomebodyFromAnotherVenue() {
        Employee stranger = employeeWithId(30L, "Ajeno", venueWithId(2L, "Bar B"), EmployeeRole.EMPLOYEE);
        when(employeeRepository.findById(30L)).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> timeClockService.addOnBehalf(
                new TimeClockEntryCreateRequest(30L, "CLOCK_OUT", CORRECTED_TO, "No es mi local"), manager))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(timeClockEntryRepository, never()).save(any());
    }

    @Test
    void anUnknownPunchTypeIsRejectedBeforeTouchingTheDatabase() {
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(worker));

        assertThatThrownBy(() -> timeClockService.addOnBehalf(
                new TimeClockEntryCreateRequest(10L, "ALMORZAR", CORRECTED_TO, "Inventado"), manager))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(timeClockEntryRepository, never()).save(any());
    }
}
