package com.generador.horarios.proyecto.schedule;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.preference.Preference;
import com.generador.horarios.proyecto.preference.PreferenceRepository;
import com.generador.horarios.proyecto.schedule.dto.EquityReportEntryResponse;
import com.generador.horarios.proyecto.schedule.dto.GenerateScheduleRequest;
import com.generador.horarios.proyecto.schedule.dto.ScheduleGenerationResponse;
import com.generador.horarios.proyecto.schedule.dto.ShiftAssignmentResponse;
import com.generador.horarios.proyecto.schedule.dto.UncoveredSlotResponse;
import com.generador.horarios.proyecto.schedule.engine.ConstraintViolation;
import com.generador.horarios.proyecto.schedule.engine.GenerationResult;
import com.generador.horarios.proyecto.schedule.engine.ScheduleGenerator;
import com.generador.horarios.proyecto.schedule.engine.ScheduleValidator;
import com.generador.horarios.proyecto.schedule.engine.Severity;
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
    private final ScheduleGenerator scheduleGenerator;
    private final ScheduleValidator scheduleValidator;

    public ScheduleService(
            ScheduleRepository scheduleRepository,
            VenueRepository venueRepository,
            EmployeeRepository employeeRepository,
            CoverageRequirementRepository coverageRequirementRepository,
            PreferenceRepository preferenceRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            ScheduleGenerator scheduleGenerator,
            ScheduleValidator scheduleValidator) {
        this.scheduleRepository = scheduleRepository;
        this.venueRepository = venueRepository;
        this.employeeRepository = employeeRepository;
        this.coverageRequirementRepository = coverageRequirementRepository;
        this.preferenceRepository = preferenceRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.scheduleGenerator = scheduleGenerator;
        this.scheduleValidator = scheduleValidator;
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

        rejectIfAnyHardViolation(result, preferences, coverageRequirements, weekStart);

        Schedule savedSchedule = scheduleRepository.save(schedule);
        shiftAssignmentRepository.saveAll(result.assignments());

        return toResponse(savedSchedule, result);
    }

    /**
     * Red de seguridad: el generador ya evita duras por construcción, pero la
     * sección 6 del CLAUDE.md exige revalidar siempre antes de persistir,
     * venga el cuadrante de donde venga. Si algo se escapara, no se guarda nada.
     */
    private void rejectIfAnyHardViolation(
            GenerationResult result, List<Preference> preferences, List<CoverageRequirement> coverageRequirements, LocalDate weekStart) {
        List<ConstraintViolation> hardViolations = scheduleValidator
                .validate(result.assignments(), preferences, coverageRequirements, weekStart).stream()
                .filter(v -> v.severity() == Severity.HARD)
                .toList();
        if (!hardViolations.isEmpty()) {
            String details = hardViolations.stream().map(ConstraintViolation::message).collect(Collectors.joining("; "));
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "El cuadrante generado viola restricciones duras: " + details);
        }
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
