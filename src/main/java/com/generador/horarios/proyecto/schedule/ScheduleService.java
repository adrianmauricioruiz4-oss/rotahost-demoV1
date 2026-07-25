package com.generador.horarios.proyecto.schedule;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.preference.Preference;
import com.generador.horarios.proyecto.preference.PreferenceRepository;
import com.generador.horarios.proyecto.schedule.dto.AssignmentEditResponse;
import com.generador.horarios.proyecto.schedule.dto.EditAssignmentRequest;
import com.generador.horarios.proyecto.schedule.dto.EquityReportEntryResponse;
import com.generador.horarios.proyecto.schedule.dto.GenerateScheduleRequest;
import com.generador.horarios.proyecto.schedule.dto.ScheduleGenerationResponse;
import com.generador.horarios.proyecto.schedule.dto.SchedulePublishResponse;
import com.generador.horarios.proyecto.schedule.dto.ScheduleResponse;
import com.generador.horarios.proyecto.schedule.dto.ShiftAssignmentResponse;
import com.generador.horarios.proyecto.schedule.dto.UncoveredSlotResponse;
import com.generador.horarios.proyecto.schedule.engine.ConstraintViolation;
import com.generador.horarios.proyecto.schedule.engine.GenerationResult;
import com.generador.horarios.proyecto.schedule.engine.ScheduleGenerator;
import com.generador.horarios.proyecto.schedule.engine.ScheduleValidator;
import com.generador.horarios.proyecto.schedule.engine.Severity;
import com.generador.horarios.proyecto.shift.ShiftAssignment;
import com.generador.horarios.proyecto.shift.ShiftAssignmentRepository;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.shift.ShiftTemplateRepository;
import com.generador.horarios.proyecto.venue.CoverageRequirement;
import com.generador.horarios.proyecto.venue.CoverageRequirementRepository;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Orquesta la generación de un cuadrante: reúne los datos del venue (Java
 * puro en el engine no toca repositorios), llama a ScheduleGenerator,
 * revalida el resultado con ScheduleValidator antes de persistir (obligatorio
 * según la sección 6 del CLAUDE.md, venga el cuadrante de donde venga) y solo
 * entonces guarda el Schedule (DRAFT) y sus ShiftAssignment.
 */
@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final VenueRepository venueRepository;
    private final EmployeeRepository employeeRepository;
    private final CoverageRequirementRepository coverageRequirementRepository;
    private final PreferenceRepository preferenceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;
    private final ScheduleGenerator scheduleGenerator;
    private final ScheduleValidator scheduleValidator;

    public ScheduleService(
            ScheduleRepository scheduleRepository,
            VenueRepository venueRepository,
            EmployeeRepository employeeRepository,
            CoverageRequirementRepository coverageRequirementRepository,
            PreferenceRepository preferenceRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            ShiftTemplateRepository shiftTemplateRepository,
            ScheduleGenerator scheduleGenerator,
            ScheduleValidator scheduleValidator) {
        this.scheduleRepository = scheduleRepository;
        this.venueRepository = venueRepository;
        this.employeeRepository = employeeRepository;
        this.coverageRequirementRepository = coverageRequirementRepository;
        this.preferenceRepository = preferenceRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.shiftTemplateRepository = shiftTemplateRepository;
        this.scheduleGenerator = scheduleGenerator;
        this.scheduleValidator = scheduleValidator;
    }

    /**
     * Lee un cuadrante ya generado (no crea nada). Lo usa la vista del empleado
     * (T3.5) para ver su semana sin depender del scheduleId, que no conoce.
     */
    @Transactional(readOnly = true)
    public ScheduleResponse findByVenueAndWeek(Long venueId, int isoYear, int isoWeek) {
        venueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue no encontrado: " + venueId));
        Schedule schedule = scheduleRepository.findByVenueIdAndIsoYearAndIsoWeek(venueId, isoYear, isoWeek)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No hay cuadrante generado para el venue " + venueId + " en " + isoYear + "-W" + isoWeek));

        List<ShiftAssignmentResponse> assignments = shiftAssignmentRepository.findByScheduleId(schedule.getId()).stream()
                .map(a -> new ShiftAssignmentResponse(a.getId(), a.getEmployee().getId(), a.getShiftTemplate().getId(), a.getDate()))
                .toList();

        return new ScheduleResponse(
                schedule.getId(), schedule.getVenue().getId(), schedule.getIsoYear(), schedule.getIsoWeek(),
                schedule.getStatus().name(), assignments);
    }

    /** Para que el controller pueda comprobar el venue antes de editar/publicar, sin exponer la entidad. */
    @Transactional(readOnly = true)
    public Long resolveVenueId(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule no encontrado: " + scheduleId))
                .getVenue().getId();
    }

    @Transactional
    public ScheduleGenerationResponse generate(GenerateScheduleRequest request) {
        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue no encontrado: " + request.venueId()));

        if (scheduleRepository.existsByVenueIdAndIsoYearAndIsoWeek(venue.getId(), request.isoYear(), request.isoWeek())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un cuadrante para el venue " + venue.getId() + " en " + request.isoYear() + "-W" + request.isoWeek());
        }

        LocalDate weekStart = deriveWeekStart(request.isoYear(), request.isoWeek());

        List<Employee> employees = employeeRepository.findByVenueIdAndActiveTrue(venue.getId());
        List<CoverageRequirement> coverageRequirements = coverageRequirementRepository.findByVenueId(venue.getId());
        List<Preference> preferences = preferenceRepository.findByEmployeeIdIn(
                employees.stream().map(Employee::getId).toList());
        List<ShiftAssignment> historicalAssignments = shiftAssignmentRepository.findBySchedule_Venue_IdAndDateBetween(
                venue.getId(), weekStart.minusWeeks(3), weekStart.minusDays(1));

        Schedule schedule = new Schedule(venue, request.isoYear(), request.isoWeek());

        GenerationResult result = scheduleGenerator.generate(
                schedule, employees, coverageRequirements, preferences, historicalAssignments, weekStart);

        validateOrRejectHardViolations(result.assignments(), preferences, coverageRequirements, weekStart,
                "El cuadrante generado viola restricciones duras: ");

        Schedule savedSchedule = scheduleRepository.save(schedule);
        shiftAssignmentRepository.saveAll(result.assignments());

        return toResponse(savedSchedule, result);
    }

    /**
     * Cambia (o quita, si shiftTemplateId es null) la asignación de un empleado en una
     * fecha concreta de un cuadrante DRAFT, revalidando siempre la semana entera antes
     * de persistir (sección 6 del CLAUDE.md): si el cambio provoca una dura, no se
     * guarda nada y se informa de qué regla se rompe. Las blandas (ej. cobertura, H7)
     * no bloquean, se devuelven como aviso.
     */
    @Transactional
    public AssignmentEditResponse editAssignment(Long scheduleId, EditAssignmentRequest request) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule no encontrado: " + scheduleId));
        if (schedule.getStatus() != ScheduleStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se puede editar un cuadrante en DRAFT (actual: " + schedule.getStatus() + ")");
        }

        LocalDate weekStart = deriveWeekStart(schedule.getIsoYear(), schedule.getIsoWeek());
        if (request.date().isBefore(weekStart) || !request.date().isBefore(weekStart.plusDays(7))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha " + request.date() + " no pertenece a la semana " + schedule.getIsoYear() + "-W" + schedule.getIsoWeek());
        }

        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee no encontrado: " + request.employeeId()));
        if (!employee.getVenue().getId().equals(schedule.getVenue().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El empleado " + request.employeeId() + " no pertenece al venue de este cuadrante");
        }

        List<ShiftAssignment> assignments = new ArrayList<>(shiftAssignmentRepository.findByScheduleId(scheduleId));
        ShiftAssignment existing = assignments.stream()
                .filter(a -> a.getEmployee().getId().equals(request.employeeId()) && a.getDate().equals(request.date()))
                .findFirst()
                .orElse(null);
        assignments.remove(existing);

        ShiftAssignment updated = null;
        if (request.shiftTemplateId() != null) {
            ShiftTemplate shiftTemplate = shiftTemplateRepository.findById(request.shiftTemplateId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "ShiftTemplate no encontrado: " + request.shiftTemplateId()));
            updated = new ShiftAssignment(employee, shiftTemplate, schedule, request.date());
            assignments.add(updated);
        }

        List<Employee> venueEmployees = employeeRepository.findByVenueIdAndActiveTrue(schedule.getVenue().getId());
        List<Preference> preferences = preferenceRepository.findByEmployeeIdIn(
                venueEmployees.stream().map(Employee::getId).toList());
        List<CoverageRequirement> coverageRequirements = coverageRequirementRepository.findByVenueId(schedule.getVenue().getId());

        List<ConstraintViolation> violations = validateOrRejectHardViolations(
                assignments, preferences, coverageRequirements, weekStart, "La edición viola restricciones duras: ");

        if (existing != null) {
            shiftAssignmentRepository.delete(existing);
        }
        if (updated != null) {
            updated = shiftAssignmentRepository.save(updated);
        }

        List<String> softWarnings = violations.stream()
                .filter(v -> v.severity() == Severity.SOFT)
                .map(ConstraintViolation::message)
                .toList();
        ShiftAssignmentResponse assignmentResponse = updated == null ? null
                : new ShiftAssignmentResponse(updated.getId(), updated.getEmployee().getId(),
                        updated.getShiftTemplate().getId(), updated.getDate());
        return new AssignmentEditResponse(assignmentResponse, softWarnings);
    }

    /**
     * Publica un cuadrante DRAFT (bloquea su edición: editAssignment ya rechaza
     * cualquier cambio si el estado no es DRAFT). Revalida toda la semana una
     * última vez antes de publicar como red de seguridad final: generate() y
     * editAssignment() ya garantizan que nada persistido viola una dura, pero
     * publicar convierte el cuadrante en el documento que firma el encargado
     * (sección 10 del CLAUDE.md), así que se vuelve a comprobar. Las blandas
     * (huecos de cobertura) no bloquean, se devuelven como aviso.
     */
    @Transactional
    public SchedulePublishResponse publish(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule no encontrado: " + scheduleId));
        if (schedule.getStatus() != ScheduleStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se puede publicar un cuadrante en DRAFT (actual: " + schedule.getStatus() + ")");
        }

        LocalDate weekStart = deriveWeekStart(schedule.getIsoYear(), schedule.getIsoWeek());
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByScheduleId(scheduleId);
        List<Employee> venueEmployees = employeeRepository.findByVenueIdAndActiveTrue(schedule.getVenue().getId());
        List<Preference> preferences = preferenceRepository.findByEmployeeIdIn(
                venueEmployees.stream().map(Employee::getId).toList());
        List<CoverageRequirement> coverageRequirements = coverageRequirementRepository.findByVenueId(schedule.getVenue().getId());

        List<ConstraintViolation> violations = validateOrRejectHardViolations(
                assignments, preferences, coverageRequirements, weekStart, "No se puede publicar: viola restricciones duras: ");

        schedule.setStatus(ScheduleStatus.PUBLISHED);

        List<String> softWarnings = violations.stream()
                .filter(v -> v.severity() == Severity.SOFT)
                .map(ConstraintViolation::message)
                .toList();
        return new SchedulePublishResponse(schedule.getId(), schedule.getStatus().name(), softWarnings);
    }

    /**
     * Red de seguridad: el generador ya evita duras por construcción, pero la
     * sección 6 del CLAUDE.md exige revalidar siempre antes de persistir,
     * venga el cuadrante de donde venga. Si algo se escapara, no se guarda nada.
     * Devuelve todas las violaciones (incluidas las blandas) para que el llamante
     * pueda reportarlas si le interesa.
     */
    private List<ConstraintViolation> validateOrRejectHardViolations(
            List<ShiftAssignment> assignments, List<Preference> preferences,
            List<CoverageRequirement> coverageRequirements, LocalDate weekStart, String errorPrefix) {
        List<ConstraintViolation> violations = scheduleValidator.validate(assignments, preferences, coverageRequirements, weekStart);
        List<ConstraintViolation> hardViolations = violations.stream()
                .filter(v -> v.severity() == Severity.HARD)
                .toList();
        if (!hardViolations.isEmpty()) {
            String details = hardViolations.stream().map(ConstraintViolation::message).collect(Collectors.joining("; "));
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, errorPrefix + details);
        }
        return violations;
    }

    /** isoYear/isoWeek (ISO-8601) -> lunes de esa semana. */
    private LocalDate deriveWeekStart(int isoYear, int isoWeek) {
        return LocalDate.now()
                .with(IsoFields.WEEK_BASED_YEAR, isoYear)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, isoWeek)
                .with(DayOfWeek.MONDAY);
    }

    private ScheduleGenerationResponse toResponse(Schedule schedule, GenerationResult result) {
        List<ShiftAssignmentResponse> assignments = result.assignments().stream()
                .map(a -> new ShiftAssignmentResponse(a.getId(), a.getEmployee().getId(), a.getShiftTemplate().getId(), a.getDate()))
                .toList();
        List<UncoveredSlotResponse> uncoveredSlots = result.uncoveredSlots().stream()
                .map(u -> new UncoveredSlotResponse(u.date(), u.shiftTemplateId(), u.missing()))
                .toList();
        List<EquityReportEntryResponse> equityReport = result.equityReport().stream()
                .map(e -> new EquityReportEntryResponse(e.employeeId(), e.badShiftsThisWeek(), e.badShiftsWithHistory()))
                .toList();

        return new ScheduleGenerationResponse(
                schedule.getId(),
                schedule.getVenue().getId(),
                schedule.getIsoYear(),
                schedule.getIsoWeek(),
                schedule.getStatus().name(),
                assignments,
                uncoveredSlots,
                equityReport);
    }
}
