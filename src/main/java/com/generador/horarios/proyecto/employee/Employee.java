package com.generador.horarios.proyecto.employee;

import com.generador.horarios.proyecto.venue.Venue;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.HashSet;
import java.util.Set;

/**
 * Empleado del venue. contractHours solo aplica a PART_TIME; un FULL_TIME
 * tiene el máximo de 40h de H3 implícito.
 */
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false)
    private ContractType contractType;

    @Column(name = "contract_hours")
    private Integer contractHours;

    @Column(nullable = false)
    private boolean active = true;

    /** Hash BCrypt; null hasta que se le asignen credenciales (todavía no hay alta de login vía API). */
    @Column
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeRole role = EmployeeRole.EMPLOYEE;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "employee_positions", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "position", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Position> positions = new HashSet<>();

    protected Employee() {
    }

    public Employee(String name, String email, ContractType contractType, Integer contractHours, Venue venue) {
        this.name = name;
        this.email = email;
        this.contractType = contractType;
        this.contractHours = contractHours;
        this.venue = venue;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public ContractType getContractType() {
        return contractType;
    }

    public void setContractType(ContractType contractType) {
        this.contractType = contractType;
    }

    public Integer getContractHours() {
        return contractHours;
    }

    public void setContractHours(Integer contractHours) {
        this.contractHours = contractHours;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Venue getVenue() {
        return venue;
    }

    public void setVenue(Venue venue) {
        this.venue = venue;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public EmployeeRole getRole() {
        return role;
    }

    public void setRole(EmployeeRole role) {
        this.role = role;
    }

    public Set<Position> getPositions() {
        return positions;
    }

    public void setPositions(Set<Position> positions) {
        this.positions = positions;
    }
}
