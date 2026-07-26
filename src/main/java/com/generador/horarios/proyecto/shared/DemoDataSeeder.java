package com.generador.horarios.proyecto.shared;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.employee.EmployeeRole;
import com.generador.horarios.proyecto.schedule.Schedule;
import com.generador.horarios.proyecto.schedule.ScheduleRepository;
import com.generador.horarios.proyecto.schedule.engine.ConstraintViolation;
import com.generador.horarios.proyecto.schedule.engine.GenerationResult;
import com.generador.horarios.proyecto.schedule.engine.ScheduleGenerator;
import com.generador.horarios.proyecto.schedule.engine.ScheduleValidator;
import com.generador.horarios.proyecto.schedule.engine.Severity;
import com.generador.horarios.proyecto.shift.ShiftAssignmentRepository;
import com.generador.horarios.proyecto.shift.ShiftSegment;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.shift.ShiftTemplateRepository;
import com.generador.horarios.proyecto.venue.CoverageRequirement;
import com.generador.horarios.proyecto.venue.CoverageRequirementRepository;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siembra un restaurante de demo con turnos, cobertura, 5 empleados y un cuadrante ya
 * generado para la semana ISO actual, para poder ver la app poblada (panel, cuadrante) nada
 * más arrancar, sin pasos manuales. No se ejecuta en el perfil "test" y es idempotente (solo
 * siembra si la base de datos está vacía).
 */
@Component
@Profile("!test")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_PASSWORD = "demo1234";

    private final VenueRepository venueRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;
    private final CoverageRequirementRepository coverageRequirementRepository;
    private final EmployeeRepository employeeRepository;
    private final ScheduleRepository scheduleRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ScheduleGenerator scheduleGenerator;
    private final ScheduleValidator scheduleValidator;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            VenueRepository venueRepository,
            ShiftTemplateRepository shiftTemplateRepository,
            CoverageRequirementRepository coverageRequirementRepository,
            EmployeeRepository employeeRepository,
            ScheduleRepository scheduleRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            ScheduleGenerator scheduleGenerator,
            ScheduleValidator scheduleValidator,
            PasswordEncoder passwordEncoder) {
        this.venueRepository = venueRepository;
        this.shiftTemplateRepository = shiftTemplateRepository;
        this.coverageRequirementRepository = coverageRequirementRepository;
        this.employeeRepository = employeeRepository;
        this.scheduleRepository = scheduleRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.scheduleGenerator = scheduleGenerator;
        this.scheduleValidator = scheduleValidator;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (venueRepository.count() > 0) {
            return;
        }

        Venue venue = venueRepository.save(new Venue("Restaurante", LocalTime.of(8, 0), LocalTime.of(2, 0)));

        ShiftTemplate manana = shiftTemplateRepository.save(new ShiftTemplate("MAÑANA", venue,
                List.of(new ShiftSegment(LocalTime.of(8, 0), LocalTime.of(16, 0)))));
        ShiftTemplate tarde = shiftTemplateRepository.save(new ShiftTemplate("TARDE", venue,
                List.of(new ShiftSegment(LocalTime.of(16, 0), LocalTime.MIDNIGHT))));
        ShiftTemplate partido = shiftTemplateRepository.save(new ShiftTemplate("PARTIDO", venue, List.of(
                new ShiftSegment(LocalTime.of(12, 0), LocalTime.of(16, 0)),
                new ShiftSegment(LocalTime.of(20, 0), LocalTime.MIDNIGHT))));

        List<CoverageRequirement> coverageRequirements =
                coverageRequirementRepository.saveAll(buildCoverageRequirements(venue, manana, tarde, partido));

        List<Employee> employees = buildEmployees(venue);
        employees.get(0).setRole(EmployeeRole.MANAGER);
        String encodedPassword = passwordEncoder.encode(DEMO_PASSWORD);
        employees.forEach(employee -> employee.setPassword(encodedPassword));
        employeeRepository.saveAll(employees);

        seedCurrentWeekSchedule(venue, employees, coverageRequirements);

        log.info("Datos de demo cargados. Login: {} / {} (encargado); el resto de empleados usa la misma contraseña.",
                employees.get(0).getEmail(), DEMO_PASSWORD);
    }

    /**
     * Genera y publica en DRAFT el cuadrante de la semana ISO actual, igual que haría
     * ScheduleService.generate(), para que el panel principal (T4.6) y la vista de
     * cuadrante no aparezcan vacíos la primera vez que alguien entra a la demo. Sin
     * preferencias ni histórico: es la primera semana de este bar de demo.
     * Revalida siempre antes de persistir (sección 6 del CLAUDE.md); si por lo que sea
     * el generador dejara una dura, no se siembra cuadrante y se sigue sin romper el arranque.
     */
    private void seedCurrentWeekSchedule(Venue venue, List<Employee> employees, List<CoverageRequirement> coverageRequirements) {
        LocalDate today = LocalDate.now();
        int isoYear = today.get(IsoFields.WEEK_BASED_YEAR);
        int isoWeek = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        LocalDate weekStart = today
                .with(IsoFields.WEEK_BASED_YEAR, isoYear)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, isoWeek)
                .with(DayOfWeek.MONDAY);

        Schedule schedule = new Schedule(venue, isoYear, isoWeek);
        GenerationResult result = scheduleGenerator.generate(
                schedule, employees, coverageRequirements, List.of(), List.of(), weekStart);

        List<ConstraintViolation> violations =
                scheduleValidator.validate(result.assignments(), List.of(), coverageRequirements, weekStart);
        boolean hasHardViolation = violations.stream().anyMatch(v -> v.severity() == Severity.HARD);
        if (hasHardViolation) {
            log.warn("El cuadrante de demo generado viola una restricción dura; se omite sembrarlo.");
            return;
        }

        scheduleRepository.save(schedule);
        shiftAssignmentRepository.saveAll(result.assignments());
    }

    /**
     * Cobertura de un equipo pequeño (5 personas): 1 persona en MAÑANA y 1 en TARDE todos
     * los días, más un PARTIDO de refuerzo viernes y sábado, que son los días fuertes.
     *
     * <p>La cifra que manda aquí es la capacidad del equipo, no el gusto por tener el local
     * bien cubierto: 3 jornadas completas (40h) + 2 parciales (25h) son 170h contratadas,
     * y cada turno son 8h. Pedir más de 21 turnos por semana es pedir horas que nadie puede
     * hacer sin romper H3, así que el generador dejaría huecos permanentes por diseño. Con
     * 16 turnos (128h) el cuadrante sale entero y sobra margen para que las preferencias y
     * la equidad tengan algo que repartir. {@code demoCoverageFitsWithinContractedHours}
     * en DemoDataSeederTest vigila que esta relación se siga cumpliendo.
     */
    private List<CoverageRequirement> buildCoverageRequirements(
            Venue venue, ShiftTemplate manana, ShiftTemplate tarde, ShiftTemplate partido) {
        List<CoverageRequirement> requirements = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            requirements.add(new CoverageRequirement(venue, day, manana, 1));
            requirements.add(new CoverageRequirement(venue, day, tarde, 1));

            boolean isWeekendPeak = day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY;
            if (isWeekendPeak) {
                requirements.add(new CoverageRequirement(venue, day, partido, 1));
            }
        }
        return requirements;
    }

    /** Equipo reducido a 5 personas (encargada incluida): más realista para un local pequeño. */
    private List<Employee> buildEmployees(Venue venue) {
        return List.of(
                new Employee("Ana García", "ana.garcia@barlaesquina.com", ContractType.FULL_TIME, null, venue),
                new Employee("Javier Martínez", "javier.martinez@barlaesquina.com", ContractType.FULL_TIME, null, venue),
                new Employee("Laura Fernández", "laura.fernandez@barlaesquina.com", ContractType.FULL_TIME, null, venue),
                new Employee("Carlos Rodríguez", "carlos.rodriguez@barlaesquina.com", ContractType.PART_TIME, 25, venue),
                new Employee("María López", "maria.lopez@barlaesquina.com", ContractType.PART_TIME, 25, venue));
    }
}
