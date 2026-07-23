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

    protected TimeClockEntry() {
    }

    public TimeClockEntry(Employee employee, PunchType type, LocalDateTime timestamp) {
        this.employee = employee;
        this.type = type;
        this.timestamp = timestamp;
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
