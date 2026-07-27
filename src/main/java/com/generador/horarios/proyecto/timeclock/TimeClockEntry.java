package com.generador.horarios.proyecto.timeclock;

import com.generador.horarios.proyecto.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Un fichaje (entrada o salida) de un empleado. Sin relación con el cuadrante: es solo el
 * registro del hecho de fichar, tanto para empleados como para invitados (GuestAuthController).
 */
@Entity
@Table(name = "time_clock_entries")
public class TimeClockEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PunchType type;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime timestamp;

    /** Hora que tenía antes de la primera corrección. Null si nadie lo ha tocado. */
    @Column(name = "original_occurred_at")
    private LocalDateTime originalTimestamp;

    @Column(name = "corrected_at")
    private LocalDateTime correctedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_by_id")
    private Employee correctedBy;

    @Column(name = "correction_reason", length = 500)
    private String correctionReason;

    /** true si lo anotó el encargado en nombre del empleado, no el empleado al fichar. */
    @Column(name = "added_by_manager", nullable = false)
    private boolean addedByManager;

    protected TimeClockEntry() {
    }

    public TimeClockEntry(Employee employee, PunchType type, LocalDateTime timestamp) {
        this.employee = employee;
        this.type = type;
        this.timestamp = timestamp;
    }

    public TimeClockEntry(Employee employee, PunchType type, LocalDateTime timestamp, boolean addedByManager) {
        this(employee, type, timestamp);
        this.addedByManager = addedByManager;
    }

    /**
     * Cambia la hora dejando rastro. La hora original se guarda solo la primera vez: si el
     * encargado corrige dos veces, lo que interesa conservar es lo que fichó el empleado, no
     * el paso intermedio.
     *
     * @param manager encargado que hace el cambio
     * @param reason  motivo, obligatorio: es lo que se enseña si alguien pregunta
     */
    public void correctTo(LocalDateTime newTimestamp, Employee manager, String reason, LocalDateTime now) {
        if (originalTimestamp == null) {
            originalTimestamp = timestamp;
        }
        timestamp = newTimestamp;
        correctedBy = manager;
        correctionReason = reason;
        correctedAt = now;
    }

    /**
     * Deja constancia de quién anotó este fichaje y por qué, sin tocar la hora. Para los que
     * nace ya puestos a mano: no hay hora original que conservar porque no la hubo nunca.
     */
    public void annotate(Employee manager, String reason, LocalDateTime now) {
        correctedBy = manager;
        correctionReason = reason;
        correctedAt = now;
    }

    public LocalDateTime getOriginalTimestamp() {
        return originalTimestamp;
    }

    public LocalDateTime getCorrectedAt() {
        return correctedAt;
    }

    public Employee getCorrectedBy() {
        return correctedBy;
    }

    public String getCorrectionReason() {
        return correctionReason;
    }

    public boolean isAddedByManager() {
        return addedByManager;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public PunchType getType() {
        return type;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
