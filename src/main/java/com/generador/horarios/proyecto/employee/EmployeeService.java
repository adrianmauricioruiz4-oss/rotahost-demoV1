package com.generador.horarios.proyecto.employee;

import com.generador.horarios.proyecto.employee.dto.EmployeeRequest;
import com.generador.horarios.proyecto.employee.dto.EmployeeResponse;
import com.generador.horarios.proyecto.venue.Venue;
import com.generador.horarios.proyecto.venue.VenueRepository;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lógica de negocio del CRUD de Employee: valida venue existente, unicidad
 * de email y la obligatoriedad de contractHours según el tipo de contrato.
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final VenueRepository venueRepository;

    public EmployeeService(EmployeeRepository employeeRepository, VenueRepository venueRepository) {
        this.employeeRepository = employeeRepository;
        this.venueRepository = venueRepository;
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        Venue venue = findVenueOrThrow(request.venueId());
        if (employeeRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ya en uso: " + request.email());
        }
        Integer contractHours = validateContractHours(request.contractType(), request.contractHours());

        Employee employee = new Employee(request.name(), request.email(), request.contractType(), contractHours, venue);
        employee.setPositions(resolvePositions(request.positions()));
        applyCapabilities(employee, request);
        return toResponse(employeeRepository.save(employee));
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> findAll() {
        return employeeRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) {
        return toResponse(findEmployeeOrThrow(id));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = findEmployeeOrThrow(id);
        Venue venue = findVenueOrThrow(request.venueId());

        employeeRepository.findByEmail(request.email())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ya en uso: " + request.email());
                });

        Integer contractHours = validateContractHours(request.contractType(), request.contractHours());

        employee.setName(request.name());
        employee.setEmail(request.email());
        employee.setContractType(request.contractType());
        employee.setContractHours(contractHours);
        employee.setVenue(venue);
        employee.setPositions(resolvePositions(request.positions()));
        applyCapabilities(employee, request);
        return toResponse(employee);
    }

    /** Soft delete: preserva el histórico para cuadrantes ya generados. */
    @Transactional
    public void delete(Long id) {
        Employee employee = findEmployeeOrThrow(id);
        employee.setActive(false);
    }

    private Set<Position> resolvePositions(Set<Position> positions) {
        return positions == null ? Set.of() : positions;
    }

    private void applyCapabilities(Employee employee, EmployeeRequest request) {
        employee.setCanWorkSplitShift(request.canWorkSplitShift());
        employee.setCanOpen(request.canOpen());
        employee.setCanClose(request.canClose());
        employee.setMinEntryTime(request.minEntryTime());
        employee.setMaxExitTime(request.maxExitTime());
        employee.setInternalNotes(request.internalNotes());
    }

    private Integer validateContractHours(ContractType contractType, Integer contractHours) {
        if (contractType == ContractType.PART_TIME) {
            if (contractHours == null || contractHours <= 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "contractHours es obligatorio y debe ser mayor que 0 para PART_TIME");
            }
        } else if (contractHours != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "contractHours no debe indicarse para FULL_TIME");
        }
        return contractHours;
    }

    private Employee findEmployeeOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee no encontrado: " + id));
    }

    private Venue findVenueOrThrow(Long venueId) {
        return venueRepository.findById(venueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue no encontrado: " + venueId));
    }

    private EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getName(),
                employee.getEmail(),
                employee.getContractType(),
                employee.getContractHours(),
                employee.isActive(),
                employee.getVenue().getId(),
                employee.getPositions(),
                employee.isCanWorkSplitShift(),
                employee.isCanOpen(),
                employee.isCanClose(),
                employee.getMinEntryTime(),
                employee.getMaxExitTime(),
                employee.getInternalNotes());
    }
}
