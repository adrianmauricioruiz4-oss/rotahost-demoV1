package com.generador.horarios.proyecto.timeclock;

import static org.assertj.core.api.Assertions.assertThat;

import com.generador.horarios.proyecto.employee.ContractType;
import com.generador.horarios.proyecto.employee.Employee;
import com.generador.horarios.proyecto.venue.Venue;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * El recuento de jornada. Es la parte que decide si a alguien le salen las horas, así que
 * se prueba aparte y sin contexto de Spring.
 *
 * <p>Regla que se está probando: la pausa se registra siempre, pero solo resta del tiempo
 * efectivo en lo que exceda del margen que reconoce el local.
 */
class WorkedTimeTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 27);
    private static final LocalDateTime DAY_START = DAY.atStartOfDay();
    private static final LocalDateTime DAY_END = DAY.plusDays(1).atStartOfDay();
    private static final int ALLOWANCE = 15;

    private final Employee employee = new Employee(
            "Ana", "ana@test.com", ContractType.FULL_TIME, null,
            new Venue("Bar Test", LocalTime.of(8, 0), LocalTime.of(2, 0)));

    private TimeClockEntry entry(PunchType type, int hour, int minute) {
        return new TimeClockEntry(employee, type, DAY.atTime(hour, minute));
    }

    private long effective(List<TimeClockEntry> entries, LocalDateTime now) {
        return WorkedTime.effectiveMinutes(WorkedTime.toSpans(entries, now), DAY_START, DAY_END, ALLOWANCE);
    }

    @Test
    void aClosedShiftWithoutBreaksCountsWholeHours() {
        List<TimeClockEntry> entries = List.of(
                entry(PunchType.CLOCK_IN, 8, 0),
                entry(PunchType.CLOCK_OUT, 16, 0));

        assertThat(effective(entries, DAY.atTime(20, 0))).isEqualTo(8 * 60);
    }

    @Test
    void aBreakWithinTheAllowanceIsNotTakenOffTheWorker() {
        List<TimeClockEntry> entries = List.of(
                entry(PunchType.CLOCK_IN, 8, 0),
                entry(PunchType.BREAK_START, 11, 0),
                entry(PunchType.BREAK_END, 11, 15),
                entry(PunchType.CLOCK_OUT, 16, 0));

        // 8 horas de presencia, 15 minutos de pausa, exactamente el margen: no se descuenta nada.
        assertThat(effective(entries, DAY.atTime(20, 0))).isEqualTo(8 * 60);
    }

    @Test
    void onlyTheBreakTimeBeyondTheAllowanceIsDiscounted() {
        List<TimeClockEntry> entries = List.of(
                entry(PunchType.CLOCK_IN, 8, 0),
                entry(PunchType.BREAK_START, 11, 0),
                entry(PunchType.BREAK_END, 12, 0),
                entry(PunchType.CLOCK_OUT, 16, 0));

        // 8 horas de presencia menos los 45 minutos que pasan del margen de 15.
        assertThat(effective(entries, DAY.atTime(20, 0))).isEqualTo(8 * 60 - 45);
    }

    @Test
    void severalBreaksAddUpAgainstOneSingleAllowance() {
        List<TimeClockEntry> entries = List.of(
                entry(PunchType.CLOCK_IN, 8, 0),
                entry(PunchType.BREAK_START, 10, 0),
                entry(PunchType.BREAK_END, 10, 10),
                entry(PunchType.BREAK_START, 13, 0),
                entry(PunchType.BREAK_END, 13, 20),
                entry(PunchType.CLOCK_OUT, 16, 0));

        // 30 minutos de pausa en total, 15 de exceso.
        assertThat(effective(entries, DAY.atTime(20, 0))).isEqualTo(8 * 60 - 15);
    }

    @Test
    void anOpenShiftCountsUpToNow() {
        List<TimeClockEntry> entries = List.of(entry(PunchType.CLOCK_IN, 12, 4));

        assertThat(effective(entries, DAY.atTime(16, 47))).isEqualTo(4 * 60 + 43);
    }

    @Test
    void timeSpentOnAnOpenBreakStillCountsAsBreak() {
        List<TimeClockEntry> entries = List.of(
                entry(PunchType.CLOCK_IN, 12, 0),
                entry(PunchType.BREAK_START, 18, 29));

        WorkedTime.Spans spans = WorkedTime.toSpans(entries, DAY.atTime(18, 47));

        assertThat(WorkedTime.breakMinutes(spans, DAY_START, DAY_END)).isEqualTo(18);
        // 6h 47m de presencia menos los 3 minutos de pausa que pasan del margen.
        assertThat(WorkedTime.effectiveMinutes(spans, DAY_START, DAY_END, ALLOWANCE)).isEqualTo(6 * 60 + 47 - 3);
    }

    @Test
    void aNightShiftSplitsItsMinutesBetweenTheTwoDaysItTouches() {
        LocalDateTime now = DAY.plusDays(1).atTime(6, 0);
        List<TimeClockEntry> entries = List.of(
                entry(PunchType.CLOCK_IN, 20, 0),
                new TimeClockEntry(employee, PunchType.CLOCK_OUT, DAY.plusDays(1).atTime(4, 0)));
        WorkedTime.Spans spans = WorkedTime.toSpans(entries, now);

        assertThat(WorkedTime.effectiveMinutes(spans, DAY_START, DAY_END, ALLOWANCE)).isEqualTo(4 * 60);
        assertThat(WorkedTime.effectiveMinutes(
                spans, DAY_END, DAY.plusDays(2).atStartOfDay(), ALLOWANCE)).isEqualTo(4 * 60);
    }

    @Test
    void aShiftLeftOpenFromAPastDayStopsAtMidnightForThatDay() {
        // Fichó la entrada y nunca la salida: el día de ayer se corta a medianoche, no crece
        // indefinidamente. El encargado lo verá como jornada sin cerrar y la corregirá.
        List<TimeClockEntry> entries = List.of(entry(PunchType.CLOCK_IN, 22, 0));

        assertThat(effective(entries, DAY.plusDays(3).atTime(9, 0))).isEqualTo(2 * 60);
    }

    @Test
    void punchesThatDoNotPairUpAreIgnoredInsteadOfBlowingUp() {
        // Dos entradas seguidas y una vuelta de pausa sin pausa: historial imposible, pero el
        // recuento no puede petar por ello.
        List<TimeClockEntry> entries = List.of(
                entry(PunchType.CLOCK_IN, 8, 0),
                entry(PunchType.CLOCK_IN, 9, 0),
                entry(PunchType.BREAK_END, 10, 0),
                entry(PunchType.CLOCK_OUT, 16, 0));

        assertThat(effective(entries, DAY.atTime(20, 0))).isEqualTo(8 * 60);
    }

    @Test
    void noPunchesMeansNoTime() {
        assertThat(effective(List.of(), DAY.atTime(20, 0))).isZero();
    }
}
