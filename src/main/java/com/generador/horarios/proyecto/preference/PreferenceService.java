package com.generador.horarios.proyecto.preference;

import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.employee.EmployeeRepository;
import com.generador.horarios.proyecto.preference.dto.PreferenceRequest;
import com.generador.horarios.proyecto.preference.dto.PreferenceResponse;
import com.generador.horarios.proyecto.shift.ShiftTemplate;
import com.generador.horarios.proyecto.shift.ShiftTemplateRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lógica de negocio del CRUD de Preference: valida employee y shiftTemplate
 * existentes, y que los campos informados encajen con el PreferenceType.
 */
@Service
public class PreferenceService {

    private final PreferenceRepository preferenceRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;

    public PreferenceService(
            PreferenceRepository preferenceRepository,
            EmployeeRepository employeeRepository,
            ShiftTemplateRepository shiftTemplateRepository) {
        this.preferenceRepository = preferenceRepository;
        this.employeeRepository = employeeRepository;
        this.shiftTemplateRepository = shiftTemplateRepository;
    }

    @Transactional
    public PreferenceResponse create(PreferenceRequest request) {
        Employee employee = findEmployeeOrThrow(request.employeeId());
        ShiftTemplate shiftTemplate = resolveShiftTemplate(employee, request.shiftTemplateId());
        int weight = validateFieldsForType(
                request.type(), request.dayOfWeek(), shiftTemplate, request.specificDate(), request.weight());

        Preference preference = new Preference(
                employee, request.type(), request.dayOfWeek(), shiftTemplate, request.specificDate(), weight);
        return toResponse(preferenceRepository.save(preference));
    }

    @Transactional(readOnly = true)
    public List<PreferenceResponse> findAll(Long employeeId) {
        List<Preference> preferences = employeeId != null
                ? preferenceRepository.findByEmployeeId(employeeId)
                : preferenceRepository.findAll();
        return preferences.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PreferenceResponse findById(Long id) {
        return toResponse(findPreferenceOrThrow(id));
    }

    @Transactional
    public PreferenceResponse update(Long id, PreferenceRequest request) {
        Preference preference = findPreferenceOrThrow(id);
        Employee employee = findEmployeeOrThrow(request.employeeId());
        ShiftTemplate shiftTemplate = resolveShiftTemplate(employee, request.shiftTemplateId());
        int weight = validateFieldsForType(
                request.type(), request.dayOfWeek(), shiftTemplate, request.specificDate(), request.weight());

        preference.setEmployee(employee);
        preference.setType(request.type());
        preference.setDayOfWeek(request.dayOfWeek());
        preference.setShiftTemplate(shiftTemplate);
        preference.setSpecificDate(request.specificDate());
        preference.setWeight(weight);
        return toResponse(preference);
    }

    /** Hard delete: solo es una entrada para puntuar la generación, ningún FK futuro la referencia. */
    @Transactional
    public void delete(Long id) {
        Preference preference = findPreferenceOrThrow(id);
        preferenceRepository.delete(preference);
    }

    private int validateFieldsForType(
            PreferenceType type, DayOfWeek dayOfWeek, ShiftTemplate shiftTemplate, LocalDate specificDate, Integer weight) {
        switch (type) {
            case PREFERS_DAY, AVOIDS_DAY -> {
                requireNonNull(dayOfWeek, "dayOfWeek es obligatorio para " + type);
                requireNull(shiftTemplate, "shiftTemplateId no debe indicarse para " + type);
                requireNull(specificDate, "specificDate no debe indicarse para " + type);
                return requireWeight(weight, type);
            }
            case PREFERS_SHIFT, AVOIDS_SHIFT -> {
                requireNonNull(shiftTemplate, "shiftTemplateId es obligatorio para " + type);
                requireNull(dayOfWeek, "dayOfWeek no debe indicarse para " + type);
                requireNull(specificDate, "specificDate no debe indicarse para " + type);
                return requireWeight(weight, type);
            }
            case UNAVAILABLE -> {
                requireNonNull(specificDate, "specificDate es obligatorio para UNAVAILABLE");
                requireNull(dayOfWeek, "dayOfWeek no debe indicarse para UNAVAILABLE");
                requireNull(shiftTemplate, "shiftTemplateId no debe indicarse para UNAVAILABLE");
                if (weight != null) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                            "weight no aplica para UNAVAILABLE (es una restricción dura, no puntuable)");
                }
                return 0;
            }
            default -> throw new IllegalStateException("PreferenceType no soportado: " + type);
        }
    }

    private void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message);
        }
    }

    private void requireNull(Object value, String message) {
        if (value != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message);
        }
    }

    private int requireWeight(Integer weight, PreferenceType type) {
        if (weight == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "weight es obligatorio para " + type);
        }
        return weight;
    }

    private ShiftTemplate resolveShiftTemplate(Employee employee, Long shiftTemplateId) {
        if (shiftTemplateId == null) {
            return null;
        }
        ShiftTemplate shiftTemplate = shiftTemplateRepository.findById(shiftTemplateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "ShiftTemplate no encontrado: " + shiftTemplateId));
        if (!shiftTemplate.getVenue().getId().equals(employee.getVenue().getId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "El shiftTemplate " + shiftTemplateId + " no pertenece al venue del empleado");
        }
        return shiftTemplate;
    }

    private Employee findEmployeeOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee no encontrado: " + id));
    }

    private Preference findPreferenceOrThrow(Long id) {
        return preferenceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preference no encontrada: " + id));
    }

    private PreferenceResponse toResponse(Preference preference) {
        Long shiftTemplateId = preference.getShiftTemplate() != null ? preference.getShiftTemplate().getId() : null;
        return new PreferenceResponse(
                preference.getId(),
                preference.getEmployee().getId(),
                preference.getType(),
                preference.getDayOfWeek(),
                shiftTemplateId,
                preference.getSpecificDate(),
                preference.getWeight());
    }
}
