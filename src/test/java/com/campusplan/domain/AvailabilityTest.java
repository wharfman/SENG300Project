/*
 * AvailabilityTest.java
 *
 * Unit tests for Availability: merging overlapping and adjacent intervals,
 * removing time, and checking whether a student is free for a whole event.
 */
package com.campusplan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Merging, removal, and coverage rules of the {@link Availability} record. */
class AvailabilityTest {

    /** A fixed date so tests only vary the times. */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    /** Builds an interval on {@link #DAY} from "HH:mm" strings. */
    private static AvailabilityInterval on(String start, String end) {
        return new AvailabilityInterval(DAY, LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    @DisplayName("Given a new student, when availability is created empty, then they are unavailable for any time")
    void missingAvailabilityMeansUnavailable() {
        Availability none = Availability.empty("S001");

        assertEquals(List.of(), none.intervals());
        assertFalse(none.isAvailable(DAY, LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    @Test
    @DisplayName("Given overlapping intervals, when they are added, then they are merged into one")
    void mergesOverlappingIntervals() {
        Availability availability = Availability.empty("S001")
                .with(on("09:00", "11:00"))
                .with(on("10:00", "12:00"));

        assertEquals(List.of(on("09:00", "12:00")), availability.intervals());
    }

    @Test
    @DisplayName("Given adjacent intervals 13:00-14:00 and 14:00-15:00, when they are added, then they are merged into one")
    void mergesAdjacentIntervals() {
        Availability availability = Availability.empty("S001")
                .with(on("14:00", "15:00"))
                .with(on("13:00", "14:00"));

        assertEquals(List.of(on("13:00", "15:00")), availability.intervals());
    }

    @Test
    @DisplayName("Given an interval inside an existing one, when it is added, then nothing changes")
    void containedIntervalIsAbsorbed() {
        Availability availability = Availability.empty("S001")
                .with(on("09:00", "17:00"))
                .with(on("10:00", "11:00"));

        assertEquals(List.of(on("09:00", "17:00")), availability.intervals());
    }

    @Test
    @DisplayName("Given separate intervals and other dates, when they are added out of order, then they stay separate and sorted")
    void keepsSeparateIntervalsSorted() {
        AvailabilityInterval nextDay = new AvailabilityInterval(
                DAY.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0));

        Availability availability = Availability.empty("S001")
                .with(nextDay)
                .with(on("15:00", "16:00"))
                .with(on("09:00", "10:00"));

        assertEquals(List.of(on("09:00", "10:00"), on("15:00", "16:00"), nextDay),
                availability.intervals());
    }

    @Test
    @DisplayName("Given 09:00-12:00, when 10:00-11:00 is removed, then the remaining time is split in two")
    void removalSplitsInterval() {
        Availability availability = Availability.empty("S001")
                .with(on("09:00", "12:00"))
                .without(on("10:00", "11:00"));

        assertEquals(List.of(on("09:00", "10:00"), on("11:00", "12:00")), availability.intervals());
    }

    @Test
    @DisplayName("Given 09:00-12:00, when 08:00-10:00 is removed, then only the uncovered part remains")
    void removalTrimsInterval() {
        Availability availability = Availability.empty("S001")
                .with(on("09:00", "12:00"))
                .without(on("08:00", "10:00"));

        assertEquals(List.of(on("10:00", "12:00")), availability.intervals());
    }

    @Test
    @DisplayName("Given 09:00-10:00, when 10:00-11:00 is removed, then it is rejected because no availability overlaps")
    void rejectsRemovingTimeThatIsNotAvailable() {
        Availability availability = Availability.empty("S001").with(on("09:00", "10:00"));

        assertThrows(ValidationException.class, () -> availability.without(on("10:00", "11:00")));
    }

    @Test
    @DisplayName("Given availability 10:00-14:00, when checking an event, then the student is available only if the whole event is covered")
    void availableOnlyForFullyCoveredEvent() {
        Availability availability = Availability.empty("S001").with(on("10:00", "14:00"));

        assertTrue(availability.isAvailable(DAY, LocalTime.of(12, 0), LocalTime.of(14, 0)));
        assertFalse(availability.isAvailable(DAY, LocalTime.of(13, 0), LocalTime.of(14, 30)));
    }

    @Test
    @DisplayName("Given two adjacent intervals added separately, when an event spans the join, then the student is available")
    void eventAcrossMergedJoinIsCovered() {
        Availability availability = Availability.empty("S001")
                .with(on("10:00", "12:00"))
                .with(on("12:00", "14:00"));

        assertTrue(availability.isAvailable(DAY, LocalTime.of(11, 0), LocalTime.of(13, 0)));
    }

    @Test
    @DisplayName("Given a blank student ID, when availability is created, then it is rejected")
    void rejectsBlankStudentId() {
        assertThrows(ValidationException.class, () -> Availability.empty(" "));
    }
}
