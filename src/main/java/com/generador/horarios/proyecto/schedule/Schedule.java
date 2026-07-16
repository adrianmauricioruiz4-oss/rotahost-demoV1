package com.generador.horarios.proyecto.schedule;

import com.generador.horarios.proyecto.shift.ShiftAssignment;
import com.generador.horarios.proyecto.venue.Venue;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;

/** Cuadrante semanal de un venue. Único por (venue, isoYear, isoWeek). */
@Entity
@Table(name = "schedules", uniqueConstraints = @UniqueConstraint(columnNames = {"venue_id", "iso_year", "iso_week"}))
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "iso_year", nullable = false)
    private int isoYear;

    @Column(name = "iso_week", nullable = false)
    private int isoWeek;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleStatus status;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ShiftAssignment> shiftAssignments = new ArrayList<>();

    protected Schedule() {
    }

    public Schedule(Venue venue, int isoYear, int isoWeek) {
        this.venue = venue;
        this.isoYear = isoYear;
        this.isoWeek = isoWeek;
        this.status = ScheduleStatus.DRAFT;
    }

    public Long getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
    }

    public void setVenue(Venue venue) {
        this.venue = venue;
    }

    public int getIsoYear() {
        return isoYear;
    }

    public void setIsoYear(int isoYear) {
        this.isoYear = isoYear;
    }

    public int getIsoWeek() {
        return isoWeek;
    }

    public void setIsoWeek(int isoWeek) {
        this.isoWeek = isoWeek;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public void setStatus(ScheduleStatus status) {
        this.status = status;
    }

    public List<ShiftAssignment> getShiftAssignments() {
        return shiftAssignments;
    }
}
