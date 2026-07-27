package com.generador.horarios.proyecto.timeclock;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Convierte una lista de fichajes en tiempo efectivo trabajado. Java puro, sin Spring ni
 * repositorios, para poder probarlo entero sin levantar contexto.
 *
 * <p>La regla de las pausas es la que fijó el dueño del producto: la pausa se registra
 * siempre como tal, y <b>no se le descuenta al trabajador</b> mientras no se pase del margen
 * que el local reconoce ({@code breakAllowanceMinutes} del Venue). Solo el exceso sobre ese
 * margen sale del cómputo. El margen se aplica por jornada, no por semana: si no, alguien
 * que hace una pausa larga el lunes se comería el margen del resto de la semana.
 */
final class WorkedTime {

    private WorkedTime() {
    }

    /** Un tramo con principio y fin. El fin es "ahora" si el tramo sigue abierto. */
    record Span(LocalDateTime start, LocalDateTime end) {

        /** Minutos del tramo que caen dentro de [from, to). Cero si no se solapan. */
        long minutesWithin(LocalDateTime from, LocalDateTime to) {
            LocalDateTime clippedStart = start.isBefore(from) ? from : start;
            LocalDateTime clippedEnd = end.isAfter(to) ? to : end;
            if (!clippedStart.isBefore(clippedEnd)) {
                return 0;
            }
            return Duration.between(clippedStart, clippedEnd).toMinutes();
        }
    }

    /** Los tramos de trabajo y los de pausa que salen de una lista de fichajes ordenada. */
    record Spans(List<Span> work, List<Span> breaks) {
    }

    /**
     * Recorre los fichajes en orden y los empareja. Tolera historiales incompletos —una
     * jornada sin cerrar, una pausa sin cerrar— porque en un bar pasan: en ese caso el tramo
     * se cierra en {@code now} y el encargado lo verá como jornada abierta para corregirla.
     *
     * @param entries fichajes ordenados de más antiguo a más reciente
     * @param now     instante con el que se cierran los tramos que siguen abiertos
     */
    static Spans toSpans(List<TimeClockEntry> entries, LocalDateTime now) {
        List<Span> work = new ArrayList<>();
        List<Span> breaks = new ArrayList<>();
        LocalDateTime workStart = null;
        LocalDateTime breakStart = null;

        for (TimeClockEntry entry : entries) {
            LocalDateTime at = entry.getTimestamp();
            switch (entry.getType()) {
                case CLOCK_IN -> {
                    if (workStart == null) {
                        workStart = at;
                    }
                }
                case BREAK_START -> {
                    if (workStart != null && breakStart == null) {
                        breakStart = at;
                    }
                }
                case BREAK_END -> {
                    if (breakStart != null) {
                        breaks.add(new Span(breakStart, at));
                        breakStart = null;
                    }
                }
                case CLOCK_OUT -> {
                    if (workStart != null) {
                        if (breakStart != null) {
                            breaks.add(new Span(breakStart, at));
                            breakStart = null;
                        }
                        work.add(new Span(workStart, at));
                        workStart = null;
                    }
                }
            }
        }

        if (breakStart != null) {
            breaks.add(new Span(breakStart, now));
        }
        if (workStart != null) {
            work.add(new Span(workStart, now));
        }
        return new Spans(work, breaks);
    }

    /**
     * Minutos efectivos dentro de [from, to): lo que se ha estado en el puesto menos el
     * exceso de pausa sobre el margen reconocido.
     *
     * @param allowanceMinutes minutos de pausa que el local reconoce como trabajados
     */
    static long effectiveMinutes(Spans spans, LocalDateTime from, LocalDateTime to, int allowanceMinutes) {
        long presence = spans.work().stream().mapToLong(s -> s.minutesWithin(from, to)).sum();
        long excessBreak = Math.max(0, breakMinutes(spans, from, to) - allowanceMinutes);
        return Math.max(0, presence - excessBreak);
    }

    /** Minutos de pausa dentro de [from, to), reconocidos o no. */
    static long breakMinutes(Spans spans, LocalDateTime from, LocalDateTime to) {
        return spans.breaks().stream().mapToLong(s -> s.minutesWithin(from, to)).sum();
    }
}
