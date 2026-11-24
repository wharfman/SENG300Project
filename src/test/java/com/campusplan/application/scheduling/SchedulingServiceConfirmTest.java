/*
 * SchedulingServiceConfirmTest.java
 *
 * Unit tests for SchedulingService.confirm: scheduling a chosen suggestion,
 * reserving its room, and rejecting stale or invalid choices without any change.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.Availability;
import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.EventDetails;
import com.campusplan.domain.EventStatus;
import com.campusplan.domain.Room;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.InMemoryAvailabilityRepository;
import com.campusplan.persistence.InMemoryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.campusplan.application.scheduling.SchedulingFixtures.DAY;
import static com.campusplan.application.scheduling.SchedulingFixtures.scheduled;
import static com.campusplan.application.scheduling.SchedulingFixtures.slot;
import static com.campusplan.domain.AttendanceResponse.ACCEPTED;
import static com.campusplan.domain.AttendanceResponse.PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link SchedulingService#confirm} over in-memory storage. */
class SchedulingServiceConfirmTest {

    /** Event storage shared by the service and the test. */
    private final InMemoryEventRepository events = new InMemoryEventRepository();

    /** Availability storage shared by the service and the test. */
    private final InMemoryAvailabilityRepository availability = new InMemoryAvailabilityRepository();

    /** Service under test, over two rooms that seat six. */
    private final SchedulingService service = new SchedulingService(events,
            () -> List.of(new Room("R01", "Lab", 6, Set.of()), new Room("R02", "Hub", 6, Set.of())), availability);

    /** The best suggestion for E001, taken before each test changes anything: R01 13:00-14:00. */
    private Suggestion best;

    /**
     * Stores draft E001 (S001-S004, 60 minutes, minimum 3) with everyone free 13:00-15:00,
     * then takes its best suggestion.
     */
    @BeforeEach
    void setUp() {
        events.save(Event.draft("E001", new EventDetails("Project meeting",
                List.of("S001", "S002", "S003", "S004"), DAY, DAY, 60, Set.of(), 3)));
        free(slot("13:00", "15:00"), "S001", "S002", "S003", "S004");
        best = service.suggest("E001").top().get(0);
    }

    /**
     * Records the same free time for each student.
     *
     * @param interval the free time
     * @param students student IDs
     */
    private void free(AvailabilityInterval interval, String... students) {
        for (String student : students) {
            Availability current = availability.findByStudentId(student).orElse(Availability.empty(student));
            availability.save(current.with(interval));
        }
    }

    /**
     * Confirms and expects a rejection that leaves every stored event as it was.
     *
     * @param eventId the event to confirm
     * @param chosen  the suggestion to confirm
     * @return the rejection message
     */
    private String rejectedWithoutChange(String eventId, Suggestion chosen) {
        List<Event> before = events.findAll();
        ValidationException e = assertThrows(ValidationException.class, () -> service.confirm(eventId, chosen));
        assertEquals(before, events.findAll());
        return e.getMessage();
    }

    @Test
    @DisplayName("Given a valid suggestion, when it is confirmed, then the event is SCHEDULED in that room and time, saved, and nobody is accepted automatically")
    void confirmSchedulesEvent() {
        assertEquals("R01", best.roomId());
        assertEquals(slot("13:00", "14:00"), best.slot());

        Event confirmed = service.confirm(" E001 ", best);

        assertEquals(EventStatus.SCHEDULED, confirmed.status());
        assertEquals("R01", confirmed.roomId());
        assertEquals(slot("13:00", "14:00"), confirmed.slot());
        assertEquals(confirmed, events.findById("E001").orElseThrow());
        assertEquals(Set.of(PENDING), Set.copyOf(confirmed.responses().values()));
    }

    @Test
    @DisplayName("Given a confirmed event in R01 13:00-14:00, when another event asks for suggestions, then R01 is not offered at overlapping times but is offered from 14:00")
    void confirmReservesRoom() {
        service.confirm("E001", best);
        events.save(Event.draft("E002", new EventDetails("Study group", List.of("S005", "S006"), DAY, DAY, 60, Set.of(), 2)));
        free(slot("13:00", "15:00"), "S005", "S006");

        List<String> offered = service.suggest("E002").top().stream()
                .map(s -> s.roomId() + " " + s.slot().start())
                .toList();

        assertFalse(offered.contains("R01 13:00"), offered.toString());
        assertFalse(offered.contains("R01 13:30"), offered.toString());
        assertTrue(offered.contains("R01 14:00"), offered.toString());
    }

    @Test
    @DisplayName("Given the room was booked for an overlapping time after the suggestion was shown, when it is confirmed, then it is rejected as stale and nothing changes")
    void rejectsStaleRoom() {
        events.save(scheduled("E101", "R01", slot("13:30", "14:30"), Map.of("S101", PENDING, "S102", PENDING)));

        assertEquals("That suggestion is no longer valid: room booked for E101 at 13:30-14:30.",
                rejectedWithoutChange("E001", best));
    }

    @Test
    @DisplayName("Given the room was booked for the adjacent 14:00-15:00 after the suggestion was shown, when it is confirmed, then it is scheduled")
    void allowsAdjacentBookingAtConfirm() {
        events.save(scheduled("E101", "R01", slot("14:00", "15:00"), Map.of("S101", PENDING, "S102", PENDING)));

        assertEquals(EventStatus.SCHEDULED, service.confirm("E001", best).status());
    }

    @Test
    @DisplayName("Given two invitees lost their availability after the suggestion was shown, when it is confirmed, then it is rejected for attendance and nothing changes")
    void rejectsStaleAttendance() {
        availability.save(Availability.empty("S001"));
        availability.save(Availability.empty("S002"));

        assertEquals("That suggestion is no longer valid: only 2 of 4 invitees can attend, fewer than the minimum of 3.",
                rejectedWithoutChange("E001", best));
    }

    @Test
    @DisplayName("Given two invitees accepted an overlapping event elsewhere after the suggestion was shown, when it is confirmed, then it is rejected and nothing changes")
    void rejectsWhenAcceptedElsewhere() {
        events.save(scheduled("E101", "R02", slot("13:00", "14:00"), Map.of("S001", ACCEPTED, "S002", ACCEPTED)));

        assertEquals("That suggestion is no longer valid: only 2 of 4 invitees can attend, fewer than the minimum of 3.",
                rejectedWithoutChange("E001", best));
    }

    @Test
    @DisplayName("Given an event already confirmed, when it is confirmed again, then it is rejected and stays as scheduled")
    void rejectsSecondConfirmation() {
        service.confirm("E001", best);

        assertEquals("Only DRAFT events can be scheduled; event E001 is SCHEDULED.", rejectedWithoutChange("E001", best));
    }

    @Test
    @DisplayName("Given a cancelled event, when a suggestion is confirmed for it, then it is rejected because cancelled events cannot be scheduled again")
    void rejectsCancelledEvent() {
        events.save(new Event("E002", new EventDetails("Old", List.of("S001", "S002"), DAY, DAY, 60, Set.of(), 2),
                EventStatus.CANCELLED, Map.of("S001", PENDING, "S002", PENDING)));

        assertEquals("Event E002 is CANCELLED and cannot be scheduled again.", rejectedWithoutChange("E002", best));
    }

    @Test
    @DisplayName("Given an unknown event, an unknown room, or a time outside the event's window or duration, when confirmed, then it is rejected and nothing changes")
    void rejectsInvalidChoices() {
        Suggestion unknownRoom = new Suggestion("R99", best.slot(), best.eligible(), Map.of());
        Suggestion nextDay = new Suggestion("R01", new AvailabilityInterval(DAY.plusDays(1),
                LocalTime.of(13, 0), LocalTime.of(14, 0)), best.eligible(), Map.of());
        Suggestion tooLong = new Suggestion("R01", slot("13:00", "15:00"), best.eligible(), Map.of());

        assertEquals("No event with ID E404.", rejectedWithoutChange("E404", best));
        assertEquals("No room with ID R99.", rejectedWithoutChange("E001", unknownRoom));
        assertEquals("2026-11-04 13:00-14:00 is not a possible time for event E001.", rejectedWithoutChange("E001", nextDay));
        assertEquals("2026-11-03 13:00-15:00 is not a possible time for event E001.", rejectedWithoutChange("E001", tooLong));
    }
}
