package com.generador.horarios.proyecto.shift;

import com.generador.horarios.proyecto.venue.Venue;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Turno tipo configurable por venue (ej. MAÑANA, TARDE, PARTIDO). El horario
 * real se define con uno o varios {@link ShiftSegment}.
 */
@Entity
@Table(name = "shift_templates")
public class ShiftTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "shift_template_segments", joinColumns = @JoinColumn(name = "shift_template_id"))
    @OrderColumn(name = "segment_order")
    private List<ShiftSegment> segments = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    protected ShiftTemplate() {
    }

    public ShiftTemplate(String name, Venue venue, List<ShiftSegment> segments) {
        this.name = name;
        this.venue = venue;
        this.segments = new ArrayList<>(segments);
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

    public Venue getVenue() {
        return venue;
    }

    public void setVenue(Venue venue) {
        this.venue = venue;
    }

    public List<ShiftSegment> getSegments() {
        return segments;
    }

    public void setSegments(List<ShiftSegment> segments) {
        this.segments = segments;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
