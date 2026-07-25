package com.generador.horarios.proyecto.shift;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;

/**
 * Tramo horario continuo dentro de un {@link ShiftTemplate}. Un turno PARTIDO
 * se modela como dos ShiftSegment (ej. 12:00-16:00 y 20:00-00:00).
 */
@Embeddable
public class ShiftSegment {

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected ShiftSegment() {
    }

    public ShiftSegment(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }
}
