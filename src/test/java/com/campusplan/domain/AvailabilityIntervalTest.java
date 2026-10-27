/*
 * AvailabilityIntervalTest.java
 *
 * Unit tests for AvailabilityInterval validation and the half-open overlap and
 * coverage rules, using boundary values and invalid-input partitions.
 */
package com.campusplan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validation, overlap, and coverage rules of the {@link AvailabilityInterval} record. */
class AvailabilityIntervalTest {

    /** A fixed date so tests only vary the times. */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    /** Builds an interval on {@link #DAY} from "HH:mm" strings. */
    private static AvailabilityInterval on(String start, String end) {
        return new AvailabilityInterval(DAY, LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    @DisplayName("Given the full opening hours 08:00-22:00, when an interval is created, then it is accepted")
    void acceptsFullOpeningHours() {
        AvailabilityInterval interval = on("08:00", "22:00");

        assertEquals(LocalTime.of(8, 0), interval.start());
        assertEquals(LocalTime.of(22, 0), interval.end());
    }

    @Test
    @DisplayName("Given a single 30-minute slot, when an interval is created, then it is accepted")
    void acceptsShortestInterval() {
        assertEquals(DAY, on("13:30", "14:00").date());
    }

    @ParameterizedTest
    @CsvSource({"07:30, 09:00", "21:00, 22:30", "07:00, 08:00", "22:00, 22:30"})
    @DisplayName("Given an interval outside 08:00-22:00, when it is created, then it is rejected")
    void rejectsOutsideOpeningHours(String start, String end) {
        assertThrows(ValidationException.class, () -> on(start, end));
    }

    @ParameterizedTest
    @CsvSource({"09:15, 10:00", "09:00, 10:45", "09:01, 10:00", "09:00, 09:59"})
    @DisplayName("Given a time not on a 30-minute boundary, when an interval is created, then it is rejected")
    void rejectsOffBoundaryTimes(String start, String end) {
        assertThrows(ValidationException.class, () -> on(start, end));
    }

    @Test
    @DisplayName("Given a time with seconds, when an interval is created, then it is rejected")
    void rejectsSeconds() {
        assertThrows(ValidationException.class,
                () -> new AvailabilityInterval(DAY, LocalTime.of(9, 0, 30), LocalTime.of(10, 0)));
    }

    @ParameterizedTest
    @CsvSource({"10:00, 10:00", "11:00, 10:00"})
    @DisplayName("Given a start that does not precede the end, when an interval is created, then it is rejected")
    void rejectsStartNotBeforeEnd(String start, String end) {
        assertThrows(ValidationException.class, () -> on(start, end));
    }

    @Test
    @DisplayName("Given a missing date, start, or end, when an interval is created, then it is rejected")
    void rejectsMissingFields() {
        LocalTime nine = LocalTime.of(9, 0);
        LocalTime ten = LocalTime.of(10, 0);

        assertThrows(ValidationException.class, () -> new AvailabilityInterval(null, nine, ten));
        assertThrows(ValidationException.class, () -> new AvailabilityInterval(DAY, null, ten));
        assertThrows(ValidationException.class, () -> new AvailabilityInterval(DAY, nine, null));
    }

    @Test
    @DisplayName("Given intervals 13:00-14:00 and 14:00-15:00, when checked for overlap, then they do not overlap but do touch")
    void adjacentIntervalsDoNotOverlap() {
        AvailabilityInterval first = on("13:00", "14:00");
        AvailabilityInterval second = on("14:00", "15:00");

        assertFalse(first.overlaps(second));
        assertFalse(second.overlaps(first));
        assertTrue(first.overlapsOrTouches(second));
    }

    @Test
    @DisplayName("Given intervals sharing 30 minutes, when checked for overlap, then they overlap")
    void sharedSlotOverlaps() {
        assertTrue(on("13:00", "14:30").overlaps(on("14:00", "15:00")));
    }

    @Test
    @DisplayName("Given the same times on different dates, when checked for overlap, then they neither overlap nor touch")
    void differentDatesNeverOverlap() {
        AvailabilityInterval monday = on("13:00", "14:00");
        AvailabilityInterval tuesday = new AvailabilityInterval(
                DAY.plusDays(1), LocalTime.of(13, 0), LocalTime.of(14, 0));

        assertFalse(monday.overlaps(tuesday));
        assertFalse(monday.overlapsOrTouches(tuesday));
    }

    @Test
    @DisplayName("Given an interval 10:00-14:00, when coverage is checked, then only times fully inside it are covered")
    void coversOnlyCompleteIntervals() {
        AvailabilityInterval interval = on("10:00", "14:00");

        assertTrue(interval.covers(DAY, LocalTime.of(10, 0), LocalTime.of(14, 0)));
        assertTrue(interval.covers(DAY, LocalTime.of(12, 0), LocalTime.of(13, 0)));
        assertFalse(interval.covers(DAY, LocalTime.of(9, 30), LocalTime.of(11, 0)));
        assertFalse(interval.covers(DAY, LocalTime.of(13, 0), LocalTime.of(14, 30)));
        assertFalse(interval.covers(DAY.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }
}
