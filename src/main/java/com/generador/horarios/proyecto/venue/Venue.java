package com.generador.horarios.proyecto.venue;

import com.generador.horarios.proyecto.shift.ShiftTemplate;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * El local: horario de apertura y las plantillas de turno / requisitos de
 * cobertura que le pertenecen.
 */
@Entity
@Table(name = "venues")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "opening_time", nullable = false)
    private LocalTime openingTime;

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime;

    /**
     * Minutos de pausa que este local reconoce como tiempo trabajado dentro de una jornada.
     * Lo que se pase de aquí sí se descuenta. El valor por defecto son los 15 minutos del
     * art. 34.4 del Estatuto de los Trabajadores para jornadas continuadas de más de seis
     * horas, pero cada convenio y cada acuerdo de empresa pueden decir otra cosa: por eso
     * es editable y no una constante.
     */
    @Column(name = "break_allowance_minutes", nullable = false)
    private int breakAllowanceMinutes = 15;

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ShiftTemplate> shiftTemplates = new ArrayList<>();

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CoverageRequirement> coverageRequirements = new ArrayList<>();

    protected Venue() {
    }

    public Venue(String name, LocalTime openingTime, LocalTime closingTime) {
        this.name = name;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
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

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public void setOpeningTime(LocalTime openingTime) {
        this.openingTime = openingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public void setClosingTime(LocalTime closingTime) {
        this.closingTime = closingTime;
    }

    public int getBreakAllowanceMinutes() {
        return breakAllowanceMinutes;
    }

    public void setBreakAllowanceMinutes(int breakAllowanceMinutes) {
        this.breakAllowanceMinutes = breakAllowanceMinutes;
    }

    public List<ShiftTemplate> getShiftTemplates() {
        return shiftTemplates;
    }

    public List<CoverageRequirement> getCoverageRequirements() {
        return coverageRequirements;
    }
}
