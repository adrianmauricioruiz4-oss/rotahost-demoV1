package com.generador.horarios.proyecto.schedule;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.schedule.dto.NotifyTeamResponse;
import com.generador.horarios.proyecto.shift.ShiftAssignment;
import com.generador.horarios.proyecto.shift.ShiftAssignmentRepository;
import com.generador.horarios.proyecto.shift.ShiftSegment;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Avisa por correo a quien tiene turno en una semana. Nunca se dispara solo: lo llama el
 * encargado desde el cuadrante, después de publicar o de hacer un cambio de última hora.
 * El producto es copiloto, no piloto automático, y eso vale también para lo que sale del
 * sistema hacia la plantilla.
 *
 * <p>Con {@code rotateam.mail.enabled} en false —que es lo de fábrica— no se envía nada:
 * el mensaje se escribe en el log y se le dice al encargado que no ha salido. Así un
 * entorno de pruebas no puede escribirle a la plantilla de verdad por descuido.
 */
@Service
public class ScheduleNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleNotificationService.class);
    private static final Locale SPANISH = Locale.forLanguageTag("es-ES");
    private static final DateTimeFormatter DAY_AND_MONTH = DateTimeFormatter.ofPattern("d 'de' MMMM", SPANISH);

    private final ScheduleRepository scheduleRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;

    public ScheduleNotificationService(
            ScheduleRepository scheduleRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            JavaMailSender mailSender,
            @Value("${rotateam.mail.enabled:false}") boolean enabled,
            @Value("${rotateam.mail.from:}") String from) {
        this.scheduleRepository = scheduleRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    /**
     * Escribe a cada persona con turno esa semana, contándole solo los suyos.
     *
     * @param lastMinute true si es un aviso de cambio de última hora en vez de una publicación
     * @return a cuántos se ha escrito, a cuántos no se ha podido, y si el envío está activado
     * @throws ResponseStatusException 404 si el cuadrante no existe
     */
    @Transactional(readOnly = true)
    public NotifyTeamResponse notifyTeam(Long scheduleId, boolean lastMinute) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule no encontrado: " + scheduleId));

        Map<Employee, List<ShiftAssignment>> byEmployee = shiftAssignmentRepository.findByScheduleId(scheduleId).stream()
                .sorted(Comparator.comparing(ShiftAssignment::getDate))
                .collect(Collectors.groupingBy(ShiftAssignment::getEmployee, LinkedHashMap::new, Collectors.toList()));

        List<String> skipped = new ArrayList<>();
        int sent = 0;
        for (Map.Entry<Employee, List<ShiftAssignment>> entry : byEmployee.entrySet()) {
            Employee employee = entry.getKey();
            if (employee.getEmail() == null || employee.getEmail().isBlank()) {
                skipped.add(employee.getName());
                continue;
            }
            if (deliver(employee, schedule, entry.getValue(), lastMinute)) {
                sent++;
            } else {
                skipped.add(employee.getName());
            }
        }
        return new NotifyTeamResponse(enabled, sent, skipped);
    }

    private boolean deliver(Employee employee, Schedule schedule, List<ShiftAssignment> assignments, boolean lastMinute) {
        String subject = lastMinute
                ? "Cambio en tu cuadrante de la semana " + schedule.getIsoWeek()
                : "Tu cuadrante de la semana " + schedule.getIsoWeek();
        String body = buildBody(employee, schedule, assignments, lastMinute);

        if (!enabled) {
            // Sin envío configurado: queda el rastro en el log y el encargado lo sabe.
            log.info("Aviso NO enviado (envío desactivado) a {} <{}>: {}", employee.getName(), employee.getEmail(), subject);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(employee.getEmail());
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (MailException couldNotSend) {
            // Que falle un correo no puede tumbar el aviso al resto de la plantilla.
            log.warn("No se ha podido escribir a {} <{}>", employee.getName(), employee.getEmail(), couldNotSend);
            return false;
        }
    }

    /**
     * Texto llano, con el lenguaje de interfaz de DESIGN.md: frase capitalizada, sin
     * exclamaciones, sin "por favor" y sin emoji.
     */
    private String buildBody(Employee employee, Schedule schedule, List<ShiftAssignment> assignments, boolean lastMinute) {
        StringBuilder text = new StringBuilder();
        text.append("Hola, ").append(employee.getName().split(" ")[0]).append(".\n\n");
        text.append(lastMinute
                ? "Ha cambiado algo en el cuadrante de la semana " + schedule.getIsoWeek() + ". Estos son tus turnos:"
                : "Ya está publicado el cuadrante de la semana " + schedule.getIsoWeek() + ". Estos son tus turnos:");
        text.append("\n\n");

        for (ShiftAssignment assignment : assignments) {
            text.append("- ").append(dayLabel(assignment.getDate())).append(": ")
                    .append(assignment.getShiftTemplate().getName()).append(", ")
                    .append(segments(assignment)).append("\n");
        }

        text.append("\nSi algo no encaja, díselo a tu encargado.\n");
        return text.toString();
    }

    private String dayLabel(LocalDate date) {
        String weekday = date.getDayOfWeek().getDisplayName(TextStyle.FULL, SPANISH);
        return weekday.substring(0, 1).toUpperCase(SPANISH) + weekday.substring(1) + " " + date.format(DAY_AND_MONTH);
    }

    private String segments(ShiftAssignment assignment) {
        return assignment.getShiftTemplate().getSegments().stream()
                .map(this::segmentLabel)
                .collect(Collectors.joining(" y "));
    }

    private String segmentLabel(ShiftSegment segment) {
        return segment.getStartTime().toString().substring(0, 5) + " a " + segment.getEndTime().toString().substring(0, 5);
    }
}
