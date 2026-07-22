package com.generador.horarios.proyecto.shared;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.employee.EmployeeRole;
import com.generador.horarios.proyecto.shift.ShiftSegment;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.shift.ShiftTemplateRepository;
import com.generador.horarios.proyecto.venue.CoverageRequirement;
import com.generador.horarios.proyecto.venue.CoverageRequirementRepository;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
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
 * Siembra un bar de demo con turnos, cobertura y 10 empleados al arrancar,
 * para poder probar los endpoints CRUD ya construidos sin datos manuales.
 * No se ejecuta en el perfil "test" y es idempotente (solo siembra si la
 * base de datos está vacía).
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
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            VenueRepository venueRepository,
            ShiftTemplateRepository shiftTemplateRepository,
            CoverageRequirementRepository coverageRequirementRepository,
            EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder) {
        this.venueRepository = venueRepository;
        this.shiftTemplateRepository = shiftTemplateRepository;
        this.coverageRequirementRepository = coverageRequirementRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (venueRepository.count() > 0) {
            return;
        }

        Venue venue = venueRepository.save(new Venue("Bar La Esquina", LocalTime.of(8, 0), LocalTime.of(2, 0)));

        ShiftTemplate manana = shiftTemplateRepository.save(new ShiftTemplate("MAÑANA", venue,
                List.of(new ShiftSegment(LocalTime.of(8, 0), LocalTime.of(16, 0)))));
        ShiftTemplate tarde = shiftTemplateRepository.save(new ShiftTemplate("TARDE", venue,
                List.of(new ShiftSegment(LocalTime.of(16, 0), LocalTime.MIDNIGHT))));
        ShiftTemplate partido = shiftTemplateRepository.save(new ShiftTemplate("PARTIDO", venue, List.of(
                new ShiftSegment(LocalTime.of(12, 0), LocalTime.of(16, 0)),
                new ShiftSegment(LocalTime.of(20, 0), LocalTime.MIDNIGHT))));

        coverageRequirementRepository.saveAll(buildCoverageRequirements(venue, manana, tarde, partido));

        List<Employee> employees = buildEmployees(venue);
        employees.get(0).setRole(EmployeeRole.MANAGER);
        String encodedPassword = passwordEncoder.encode(DEMO_PASSWORD);
        employees.forEach(employee -> employee.setPassword(encodedPassword));
        employeeRepository.saveAll(employees);

        log.info("Datos de demo cargados. Login: {} / {} (encargado); el resto de empleados usa la misma contraseña.",
                employees.get(0).getEmail(), DEMO_PASSWORD);
    }

    /** Refuerzo de plantilla en TARDE/PARTIDO los viernes y sábados. */
    private List<CoverageRequirement> buildCoverageRequirements(
            Venue venue, ShiftTemplate manana, ShiftTemplate tarde, ShiftTemplate partido) {
        List<CoverageRequirement> requirements = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            boolean isWeekendPeak = day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY;
            requirements.add(new CoverageRequirement(venue, day, manana, 2));
            requirements.add(new CoverageRequirement(venue, day, tarde, isWeekendPeak ? 3 : 2));
            requirements.add(new CoverageRequirement(venue, day, partido, isWeekendPeak ? 2 : 1));
        }
        return requirements;
    }

    private List<Employee> buildEmployees(Venue venue) {
        return List.of(
                new Employee("Ana García", "ana.garcia@barlaesquina.com", ContractType.FULL_TIME, null, venue),
                new Employee("Javier Martínez", "javier.martinez@barlaesquina.com", ContractType.FULL_TIME, null, venue),
                new Employee("Laura Fernández", "laura.fernandez@barlaesquina.com", ContractType.FULL_TIME, null, venue),
                new Employee("Carlos Rodríguez", "carlos.rodriguez@barlaesquina.com", ContractType.FULL_TIME, null, venue),
                new Employee("María López", "maria.lopez@barlaesquina.com", ContractType.FULL_TIME, null, venue),
                new Employee("David Sánchez", "david.sanchez@barlaesquina.com", ContractType.PART_TIME, 20, venue),
                new Employee("Sara Pérez", "sara.perez@barlaesquina.com", ContractType.PART_TIME, 25, venue),
                new Employee("Pablo Gómez", "pablo.gomez@barlaesquina.com", ContractType.PART_TIME, 30, venue),
                new Employee("Lucía Díaz", "lucia.diaz@barlaesquina.com", ContractType.PART_TIME, 20, venue),
                new Employee("Miguel Ruiz", "miguel.ruiz@barlaesquina.com", ContractType.PART_TIME, 25, venue));
    }
}
