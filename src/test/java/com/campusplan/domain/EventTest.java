/*
 * EventTest.java
 *
 * Unit tests for Event: new drafts, editing drafts, keeping one response
 * per invitee, and the room and time of scheduled events.
 */
package com.campusplan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Draft creation, editing, and response rules of the {@link Event} record. */
class EventTest {

    /** First day of the date window. */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    /** Valid details inviting the given students. */
    private static EventDetails inviting(String... ids) {
        return new EventDetails("Meeting", List.of(ids), DAY, DAY, 60, Set.of(), 2);
    }

    @Test
    @DisplayName("Given valid details, when a draft is created, then its status is DRAFT and every invitee is PENDING")
    void newDraftIsPending() {
        Event event = Event.draft("E001", inviting("S001", "S002", "S003"));

        assertEquals(EventStatus.DRAFT, event.status());
        assertEquals(List.of("S001", "S002", "S003"), List.copyOf(event.responses().keySet()));
        assertEquals(Set.of(AttendanceResponse.PENDING), Set.copyOf(event.responses().values()));
    }

    @Test
    @DisplayName("Given a draft, when its details are changed, then the ID and status stay and new invitees are PENDING")
    void editingDraftKeepsIdentity() {
        Event event = Event.draft("E001", inviting("S001", "S002"));

        Event edited = event.withDetails(inviting("S002", "S003"));

        assertEquals("E001", edited.id());
        assertEquals(EventStatus.DRAFT, edited.status());
        assertEquals(Map.of("S002", AttendanceResponse.PENDING, "S003", AttendanceResponse.PENDING),
                edited.responses());
    }

    @Test
    @DisplayName("Given a draft with a recorded response, when invitees are added and removed, then kept invitees keep their response, new ones are PENDING, and removed ones are dropped")
    void editingDraftKeepsExistingResponses() {
        Event event = new Event("E001", inviting("S001", "S002", "S003"), EventStatus.DRAFT, Map.of(
                "S001", AttendanceResponse.DECLINED,
                "S002", AttendanceResponse.PENDING,
                "S003", AttendanceResponse.PENDING));

        Event edited = event.withDetails(inviting("S001", "S002", "S004"));

        assertEquals(Map.of(
                "S001", AttendanceResponse.DECLINED,
                "S002", AttendanceResponse.PENDING,
                "S004", AttendanceResponse.PENDING), edited.responses());
    }

    @Test
    @DisplayName("Given a cancelled event, when its details are changed, then it is rejected")
    void onlyDraftsCanBeEdited() {
        Event cancelled = new Event("E001", inviting("S001", "S002"), EventStatus.CANCELLED,
                Map.of("S001", AttendanceResponse.PENDING, "S002", AttendanceResponse.PENDING));

        ValidationException e = assertThrows(ValidationException.class,
                () -> cancelled.withDetails(inviting("S001", "S003")));
        assertEquals("Only DRAFT events can be edited; event E001 is CANCELLED.", e.getMessage());
    }

    @Test
    @DisplayName("Given responses that do not match the invitees, when an event is created, then it is rejected")
    void rejectsResponsesNotMatchingInvitees() {
        assertThrows(ValidationException.class, () -> new Event("E001", inviting("S001", "S002"),
                EventStatus.DRAFT, Map.of("S001", AttendanceResponse.PENDING)));
        assertThrows(ValidationException.class, () -> new Event("E001", inviting("S001", "S002"),
                EventStatus.DRAFT, Map.of("S001", AttendanceResponse.PENDING, "S009", AttendanceResponse.PENDING)));
    }

    @Test
    @DisplayName("Given a room and time, when a SCHEDULED event is created, then it keeps both")
    void scheduledEventHasRoomAndTime() {
        AvailabilityInterval slot = new AvailabilityInterval(DAY, LocalTime.of(13, 0), LocalTime.of(14, 0));

        Event event = new Event("E001", inviting("S001", "S002"), EventStatus.SCHEDULED,
                Map.of("S001", AttendanceResponse.PENDING, "S002", AttendanceResponse.PENDING), "R01", slot);

        assertEquals("R01", event.roomId());
        assertEquals(slot, event.slot());
    }

    @Test
    @DisplayName("Given a missing room or time, when a SCHEDULED event is created, then it is rejected")
    void scheduledEventNeedsRoomAndTime() {
        AvailabilityInterval slot = new AvailabilityInterval(DAY, LocalTime.of(13, 0), LocalTime.of(14, 0));
        Map<String, AttendanceResponse> pending =
                Map.of("S001", AttendanceResponse.PENDING, "S002", AttendanceResponse.PENDING);

        ValidationException e = assertThrows(ValidationException.class, () -> new Event("E001",
                inviting("S001", "S002"), EventStatus.SCHEDULED, pending));
        assertEquals("Scheduled event E001 needs a room and a time.", e.getMessage());
        assertThrows(ValidationException.class, () -> new Event("E001", inviting("S001", "S002"),
                EventStatus.SCHEDULED, pending, " ", slot));
        assertThrows(ValidationException.class, () -> new Event("E001", inviting("S001", "S002"),
                EventStatus.SCHEDULED, pending, "R01", null));
    }

    @Test
    @DisplayName("Given a room or time, when a DRAFT or CANCELLED event is created, then it is rejected")
    void onlyScheduledEventsHaveRoomAndTime() {
        AvailabilityInterval slot = new AvailabilityInterval(DAY, LocalTime.of(13, 0), LocalTime.of(14, 0));
        Map<String, AttendanceResponse> pending =
                Map.of("S001", AttendanceResponse.PENDING, "S002", AttendanceResponse.PENDING);

        ValidationException e = assertThrows(ValidationException.class, () -> new Event("E001",
                inviting("S001", "S002"), EventStatus.DRAFT, pending, "R01", slot));
        assertEquals("Only SCHEDULED events have a room and time; event E001 is DRAFT.", e.getMessage());
        assertThrows(ValidationException.class, () -> new Event("E001", inviting("S001", "S002"),
                EventStatus.CANCELLED, pending, null, slot));
    }

    @Test
    @DisplayName("Given a draft with a DECLINED response, when it is scheduled, then it is SCHEDULED in that room and time and every response is unchanged")
    void schedulingKeepsResponses() {
        AvailabilityInterval slot = new AvailabilityInterval(DAY, LocalTime.of(13, 0), LocalTime.of(14, 0));
        Event draft = new Event("E001", inviting("S001", "S002"), EventStatus.DRAFT,
                Map.of("S001", AttendanceResponse.DECLINED, "S002", AttendanceResponse.PENDING));

        Event scheduled = draft.schedule("R01", slot);

        assertEquals(EventStatus.SCHEDULED, scheduled.status());
        assertEquals("R01", scheduled.roomId());
        assertEquals(slot, scheduled.slot());
        assertEquals(draft.responses(), scheduled.responses());
    }

    @Test
    @DisplayName("Given a SCHEDULED or CANCELLED event, when it is scheduled, then it is rejected and a cancelled event says it cannot be scheduled again")
    void onlyDraftsCanBeScheduled() {
        AvailabilityInterval slot = new AvailabilityInterval(DAY, LocalTime.of(13, 0), LocalTime.of(14, 0));
        Map<String, AttendanceResponse> pending =
                Map.of("S001", AttendanceResponse.PENDING, "S002", AttendanceResponse.PENDING);
        Event scheduled = new Event("E001", inviting("S001", "S002"), EventStatus.SCHEDULED, pending, "R01", slot);
        Event cancelled = new Event("E002", inviting("S001", "S002"), EventStatus.CANCELLED, pending);

        ValidationException again = assertThrows(ValidationException.class, () -> scheduled.schedule("R02", slot));
        ValidationException dead = assertThrows(ValidationException.class, () -> cancelled.schedule("R01", slot));

        assertEquals("Only DRAFT events can be scheduled; event E001 is SCHEDULED.", again.getMessage());
        assertEquals("Event E002 is CANCELLED and cannot be scheduled again.", dead.getMessage());
    }

    @Test
    @DisplayName("Given a blank ID, missing details, or missing status, when an event is created, then it is rejected")
    void rejectsMissingFields() {
        assertThrows(ValidationException.class, () -> Event.draft(" ", inviting("S001", "S002")));
        assertThrows(ValidationException.class, () -> Event.draft("E001", null));
        assertThrows(ValidationException.class, () -> new Event("E001", inviting("S001", "S002"), null, null));
    }
}
