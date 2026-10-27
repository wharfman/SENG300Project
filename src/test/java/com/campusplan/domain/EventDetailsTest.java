/*
 * EventDetailsTest.java
 *
 * Unit tests for EventDetails validation and normalization: title, invitees,
 * date window, duration, and minimum attendance, on both sides of every limit.
 */
package com.campusplan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Validation and normalization rules of the {@link EventDetails} record. */
class EventDetailsTest {

    /** First day of the date window used by most tests. */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    /** Two invitees, the smallest valid list. */
    private static final List<String> TWO = List.of("S001", "S002");

    /** Builds a list of invitee IDs S001..Snnn. */
    private static List<String> invitees(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> String.format("S%03d", i)).toList();
    }

    /** Valid details with the given title; every other field is valid. */
    private static EventDetails titled(String title) {
        return new EventDetails(title, TWO, DAY, DAY, 60, Set.of(), 2);
    }

    @Test
    @DisplayName("Given valid fields with padding, when details are created, then the title and invitee IDs are trimmed and features kept")
    void normalizesFields() {
        EventDetails details = new EventDetails("  Project meeting  ", List.of(" S001", "S002 "),
                DAY, DAY.plusDays(2), 120, Set.of(RoomFeature.WHITEBOARD), 2);

        assertEquals("Project meeting", details.title());
        assertEquals(List.of("S001", "S002"), details.inviteeIds());
        assertEquals(EnumSet.of(RoomFeature.WHITEBOARD), details.requiredFeatures());
        assertEquals(3, details.windowDays());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    @DisplayName("Given a title of 1 or 100 characters, when details are created, then it is accepted")
    void acceptsBoundaryTitleLength(int length) {
        String title = "t".repeat(length);

        assertEquals(title, titled(title).title());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Given a title that is empty after trimming, when details are created, then it is rejected")
    void rejectsBlankTitle(String title) {
        assertThrows(ValidationException.class, () -> titled(title));
    }

    @Test
    @DisplayName("Given a 101-character title, when details are created, then it is rejected")
    void rejectsTooLongTitle() {
        assertThrows(ValidationException.class, () -> titled("t".repeat(101)));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 20})
    @DisplayName("Given 2 or 20 invitees, when details are created, then they are accepted")
    void acceptsBoundaryInviteeCount(int count) {
        EventDetails details = new EventDetails("Meeting", invitees(count), DAY, DAY, 60, Set.of(), 2);

        assertEquals(count, details.inviteeIds().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 21})
    @DisplayName("Given 0, 1, or 21 invitees, when details are created, then they are rejected")
    void rejectsInviteeCountOutOfRange(int count) {
        assertThrows(ValidationException.class,
                () -> new EventDetails("Meeting", invitees(count), DAY, DAY, 60, Set.of(), 2));
    }

    @Test
    @DisplayName("Given the same invitee twice, even with padding, when details are created, then it is rejected")
    void rejectsDuplicateInvitees() {
        assertThrows(ValidationException.class,
                () -> new EventDetails("Meeting", List.of("S001", " S001 ", "S002"), DAY, DAY, 60, Set.of(), 2));
    }

    @Test
    @DisplayName("Given a missing or blank invitee ID, when details are created, then it is rejected")
    void rejectsBlankInvitee() {
        assertThrows(ValidationException.class,
                () -> new EventDetails("Meeting", List.of("S001", " "), DAY, DAY, 60, Set.of(), 2));
        assertThrows(ValidationException.class,
                () -> new EventDetails("Meeting", null, DAY, DAY, 60, Set.of(), 2));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    @DisplayName("Given a window of 1 or 7 days, when details are created, then it is accepted")
    void acceptsBoundaryWindow(int extraDays) {
        EventDetails details = new EventDetails("Meeting", TWO, DAY, DAY.plusDays(extraDays), 60, Set.of(), 2);

        assertEquals(extraDays + 1, details.windowDays());
    }

    @Test
    @DisplayName("Given an 8-day window, when details are created, then it is rejected")
    void rejectsWindowOverSevenDays() {
        assertThrows(ValidationException.class,
                () -> new EventDetails("Meeting", TWO, DAY, DAY.plusDays(7), 60, Set.of(), 2));
    }

    @Test
    @DisplayName("Given a last day before the first day, when details are created, then it is rejected")
    void rejectsWindowEndingBeforeItStarts() {
        assertThrows(ValidationException.class,
                () -> new EventDetails("Meeting", TWO, DAY, DAY.minusDays(1), 60, Set.of(), 2));
    }

    @Test
    @DisplayName("Given a missing first or last day, when details are created, then it is rejected")
    void rejectsMissingWindow() {
        assertThrows(ValidationException.class,
                () -> new EventDetails("Meeting", TWO, null, DAY, 60, Set.of(), 2));
        assertThrows(ValidationException.class,
                () -> new EventDetails("Meeting", TWO, DAY, null, 60, Set.of(), 2));
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 90, 180})
    @DisplayName("Given a duration of 30, 90, or 180 minutes, when details are created, then it is accepted")
    void acceptsValidDuration(int minutes) {
        assertEquals(minutes, new EventDetails("Meeting", TWO, DAY, DAY, minutes, Set.of(), 2).durationMinutes());
    }

    @ParameterizedTest
    @ValueSource(ints = {-30, 0, 15, 45, 179, 210})
    @DisplayName("Given a duration outside 30-180 or not a multiple of 30, when details are created, then it is rejected")
    void rejectsInvalidDuration(int minutes) {
        assertThrows(ValidationException.class,
                () -> new EventDetails("Meeting", TWO, DAY, DAY, minutes, Set.of(), 2));
    }

    @Test
    @DisplayName("Given minimum attendance of 2 or the invitee count, when details are created, then it is accepted")
    void acceptsBoundaryMinimumAttendance() {
        assertEquals(2, new EventDetails("Meeting", invitees(6), DAY, DAY, 60, Set.of(), 2).minimumAttendance());
        assertEquals(6, new EventDetails("Meeting", invitees(6), DAY, DAY, 60, Set.of(), 6).minimumAttendance());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7})
    @DisplayName("Given minimum attendance below 2 or above the invitee count, when details are created, then it is rejected")
    void rejectsMinimumAttendanceOutOfRange(int minimum) {
        assertThrows(ValidationException.class,
                () -> new EventDetails("Meeting", invitees(6), DAY, DAY, 60, Set.of(), minimum));
    }

    @Test
    @DisplayName("Given no required features, when details are created, then the feature set is empty")
    void nullFeaturesMeansNone() {
        assertEquals(Set.of(), new EventDetails("Meeting", TWO, DAY, DAY, 60, null, 2).requiredFeatures());
    }
}
