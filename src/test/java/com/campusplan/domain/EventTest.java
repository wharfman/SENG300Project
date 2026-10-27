/*
 * EventTest.java
 *
 * Unit tests for Event: new drafts, editing drafts, and keeping one response
 * per invitee.
 */
package com.campusplan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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

        assertThrows(ValidationException.class, () -> cancelled.withDetails(inviting("S001", "S003")));
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
    @DisplayName("Given a blank ID, missing details, or missing status, when an event is created, then it is rejected")
    void rejectsMissingFields() {
        assertThrows(ValidationException.class, () -> Event.draft(" ", inviting("S001", "S002")));
        assertThrows(ValidationException.class, () -> Event.draft("E001", null));
        assertThrows(ValidationException.class, () -> new Event("E001", inviting("S001", "S002"), null, null));
    }
}
