package com.generador.horarios.proyecto.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.schedule.dto.NotifyTeamResponse;
import com.generador.horarios.proyecto.shift.ShiftAssignment;
import com.generador.horarios.proyecto.shift.ShiftAssignmentRepository;
import com.generador.horarios.proyecto.shift.ShiftSegment;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.venue.Venue;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Avisos por correo. Lo importante aquí es que sin envío configurado no salga nada, que
 * cada persona reciba solo sus turnos, y que un fallo suelto no deje sin avisar al resto.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleNotificationServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private JavaMailSender mailSender;

    private Venue venue;
    private Schedule schedule;
    private ShiftTemplate morning;

    @BeforeEach
    void setUp() {
        venue = new Venue("Restaurante El Mirador", LocalTime.of(8, 0), LocalTime.of(2, 0));
        ReflectionTestUtils.setField(venue, "id", 1L);

        schedule = new Schedule(venue, 2026, 31);
        ReflectionTestUtils.setField(schedule, "id", 900L);

        morning = new ShiftTemplate("MAÑANA", venue,
                List.of(new ShiftSegment(LocalTime.of(8, 0), LocalTime.of(16, 0))));
        ReflectionTestUtils.setField(morning, "id", 100L);
    }

    private Employee employee(Long id, String name, String email) {
        Employee created = new Employee(name, email, ContractType.FULL_TIME, null, venue);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    private ShiftAssignment assignment(Employee employee, int day) {
        return new ShiftAssignment(employee, morning, schedule, LocalDate.of(2026, 7, day));
    }

    private ScheduleNotificationService serviceWithMail(boolean enabled) {
        return new ScheduleNotificationService(
                scheduleRepository, shiftAssignmentRepository, mailSender, enabled, "avisos@rotateam.test");
    }

    @Test
    void withMailTurnedOffNothingLeavesTheBuilding() {
        Employee ana = employee(10L, "Ana García", "ana@test.com");
        when(scheduleRepository.findById(900L)).thenReturn(Optional.of(schedule));
        when(shiftAssignmentRepository.findByScheduleId(900L)).thenReturn(List.of(assignment(ana, 27)));

        NotifyTeamResponse response = serviceWithMail(false).notifyTeam(900L, false);

        assertThat(response.mailEnabled()).isFalse();
        assertThat(response.sent()).isZero();
        assertThat(response.skipped()).containsExactly("Ana García");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void eachPersonIsToldOnlyTheirOwnShifts() {
        Employee ana = employee(10L, "Ana García", "ana@test.com");
        Employee luis = employee(11L, "Luis Prado", "luis@test.com");
        when(scheduleRepository.findById(900L)).thenReturn(Optional.of(schedule));
        when(shiftAssignmentRepository.findByScheduleId(900L)).thenReturn(
                List.of(assignment(ana, 27), assignment(luis, 28), assignment(ana, 29)));

        NotifyTeamResponse response = serviceWithMail(true).notifyTeam(900L, false);

        assertThat(response.sent()).isEqualTo(2);
        ArgumentCaptor<SimpleMailMessage> sentMail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.times(2)).send(sentMail.capture());

        SimpleMailMessage toAna = sentMail.getAllValues().stream()
                .filter(m -> m.getTo()[0].equals("ana@test.com")).findFirst().orElseThrow();
        assertThat(toAna.getSubject()).isEqualTo("Tu cuadrante de la semana 31");
        assertThat(toAna.getText()).contains("Hola, Ana").contains("Lunes 27 de julio").contains("08:00 a 16:00");
        // Los turnos de Luis no aparecen en el correo de Ana.
        assertThat(toAna.getText()).doesNotContain("28 de julio");
    }

    @Test
    void aLastMinuteNoticeIsWordedAsAChangeNotAsAPublication() {
        Employee ana = employee(10L, "Ana García", "ana@test.com");
        when(scheduleRepository.findById(900L)).thenReturn(Optional.of(schedule));
        when(shiftAssignmentRepository.findByScheduleId(900L)).thenReturn(List.of(assignment(ana, 27)));

        serviceWithMail(true).notifyTeam(900L, true);

        ArgumentCaptor<SimpleMailMessage> sentMail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sentMail.capture());
        assertThat(sentMail.getValue().getSubject()).isEqualTo("Cambio en tu cuadrante de la semana 31");
        assertThat(sentMail.getValue().getText()).contains("Ha cambiado algo");
    }

    @Test
    void somebodyWithoutAnEmailIsReportedInsteadOfSilentlyDropped() {
        Employee sinCorreo = employee(12L, "Marco Gil", "");
        when(scheduleRepository.findById(900L)).thenReturn(Optional.of(schedule));
        when(shiftAssignmentRepository.findByScheduleId(900L)).thenReturn(List.of(assignment(sinCorreo, 27)));

        NotifyTeamResponse response = serviceWithMail(true).notifyTeam(900L, false);

        assertThat(response.sent()).isZero();
        assertThat(response.skipped()).containsExactly("Marco Gil");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void oneFailedDeliveryDoesNotStopTheRestOfTheTeamBeingTold() {
        Employee ana = employee(10L, "Ana García", "ana@test.com");
        Employee luis = employee(11L, "Luis Prado", "luis@test.com");
        when(scheduleRepository.findById(900L)).thenReturn(Optional.of(schedule));
        when(shiftAssignmentRepository.findByScheduleId(900L)).thenReturn(
                List.of(assignment(ana, 27), assignment(luis, 28)));
        org.mockito.Mockito.doThrow(new MailSendException("buzón lleno"))
                .when(mailSender).send(org.mockito.ArgumentMatchers.argThat(
                        (SimpleMailMessage m) -> m != null && m.getTo() != null && m.getTo()[0].equals("ana@test.com")));

        NotifyTeamResponse response = serviceWithMail(true).notifyTeam(900L, false);

        assertThat(response.sent()).isEqualTo(1);
        assertThat(response.skipped()).containsExactly("Ana García");
    }
}
