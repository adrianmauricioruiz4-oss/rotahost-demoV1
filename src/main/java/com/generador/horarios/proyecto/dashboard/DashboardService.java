package com.generador.horarios.proyecto.dashboard;

import com.generador.horarios.proyecto.dashboard.dto.DashboardSummaryResponse;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.preference.Preference;
import com.generador.horarios.proyecto.preference.PreferenceRepository;
import com.generador.horarios.proyecto.schedule.Schedule;
import com.generador.horarios.proyecto.schedule.ScheduleRepository;
import com.generador.horarios.proyecto.schedule.engine.ConstraintViolation;
import com.generador.horarios.proyecto.schedule.engine.ScheduleValidator;
import com.generador.horarios.proyecto.shift.ShiftAssignment;
import com.generador.horarios.proyecto.shift.ShiftAssignmentRepository;
import com.generador.horarios.proyecto.venue.CoverageRequirement;
import com.generador.horarios.proyecto.venue.CoverageRequirementRepository;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Agrega, para el venue del usuario autenticado, lo que necesita el panel principal
 * (T4.6): nombre del local, nº de empleados activos, estado del cuadrante de la semana
 * ISO actual y sus alertas. No crea ni modifica nada, es una vista de solo lectura sobre
 * datos ya persistidos por los demás features.
 */
@Service
public class DashboardService {

    private final VenueRepository venueRepository;
    private final EmployeeRepository employeeRepository;
    private final ScheduleRepository scheduleRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final PreferenceRepository preferenceRepository;
    private final CoverageRequirementRepository coverageRequirementRepository;
    private final ScheduleValidator scheduleValidator;

    public DashboardService(
            VenueRepository venueRepository,
            EmployeeRepository employeeRepository,
            ScheduleRepository scheduleRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            PreferenceRepository preferenceRepository,
            CoverageRequirementRepository coverageRequirementRepository,
            ScheduleValidator scheduleValidator) {
        this.venueRepository = venueRepository;
        this.employeeRepository = employeeRepository;
        this.scheduleRepository = scheduleRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.preferenceRepository = preferenceRepository;
        this.coverageRequirementRepository = coverageRequirementRepository;
        this.scheduleValidator = scheduleValidator;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(Long venueId) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue no encontrado: " + venueId));

        List<Employee> activeEmployees = employeeRepository.findByVenueIdAndActiveTrue(venueId);

        LocalDate today = LocalDate.now();
        int isoYear = today.get(IsoFields.WEEK_BASED_YEAR);
        int isoWeek = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);

        Optional<Schedule> currentSchedule = scheduleRepository.findByVenueIdAndIsoYearAndIsoWeek(venueId, isoYear, isoWeek);

        String scheduleStatus = currentSchedule.map(schedule -> schedule.getStatus().name()).orElse(null);
        List<String> alerts = currentSchedule
                .map(schedule -> buildAlerts(schedule, activeEmployees, venueId))
                .orElseGet(() -> List.of(
                        "No hay cuadrante generado para la semana " + isoYear + "-W" + isoWeek + " todavía."));

        return new DashboardSummaryResponse(venue.getName(), activeEmployees.size(), isoYear, isoWeek, scheduleStatus, alerts);
    }

    /**
     * Revalida el cuadrante actual con las preferencias y la cobertura de hoy (no las que
     * había cuando se generó): así el panel avisa de conflictos sobrevenidos, por ejemplo
     * una baja marcada como UNAVAILABLE después de publicar el cuadrante de esa semana.
     * Se listan todas las violaciones (duras y blandas) como texto plano; distinguir
     * severidad aquí no aporta al encargado, que solo quiere saber qué mirar. Se antepone
     * el nombre del empleado cuando la violación señala a uno concreto (H7, de cobertura,
     * no lo hace), para que el aviso se lea en lenguaje natural (ver T7.6 del roadmap).
     */
    private List<String> buildAlerts(Schedule schedule, List<Employee> activeEmployees, Long venueId) {
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByScheduleId(schedule.getId());
        List<Preference> preferences = preferenceRepository.findByEmployeeIdIn(
                activeEmployees.stream().map(Employee::getId).toList());
        List<CoverageRequirement> coverageRequirements = coverageRequirementRepository.findByVenueId(venueId);
        LocalDate weekStart = deriveWeekStart(schedule.getIsoYear(), schedule.getIsoWeek());

        Map<Long, String> namesById = activeEmployees.stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));

        List<ConstraintViolation> violations =
                scheduleValidator.validate(assignments, preferences, coverageRequirements, weekStart);
        return violations.stream().map(violation -> describeViolation(violation, namesById)).toList();
    }

    private String describeViolation(ConstraintViolation violation, Map<Long, String> namesById) {
        if (violation.employeeId() == null) {
            return violation.message();
        }
        String name = namesById.get(violation.employeeId());
        return name == null ? violation.message() : name + " — " + violation.message();
    }

    /** isoYear/isoWeek (ISO-8601) -> lunes de esa semana. Igual que en ScheduleService. */
    private LocalDate deriveWeekStart(int isoYear, int isoWeek) {
        return LocalDate.now()
                .with(IsoFields.WEEK_BASED_YEAR, isoYear)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, isoWeek)
                .with(DayOfWeek.MONDAY);
    }
}
