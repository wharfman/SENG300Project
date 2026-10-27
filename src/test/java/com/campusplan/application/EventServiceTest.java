/*
 * EventServiceTest.java
 *
 * Unit tests for EventService: creating, editing, and viewing draft events,
 * with unknown invitees, unknown events, and invalid limits as error paths.
 */
package com.campusplan.application;

import com.campusplan.domain.AttendanceResponse;
import com.campusplan.domain.Event;
import com.campusplan.domain.EventDetails;
import com.campusplan.domain.EventStatus;
import com.campusplan.domain.RoomFeature;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.InMemoryEventRepository;
import com.campusplan.persistence.InMemoryStudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link EventService} operations over in-memory storage, including rejected operations. */
class EventServiceTest {

    /** First day of the date window. */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    /** Service under test, recreated empty for each test. */
    private EventService service;

    /** Starts each test with three students, S001-S003, and no events. */
    @BeforeEach
    void setUp() {
        StudentService students = new StudentService(new InMemoryStudentRepository());
        students.create("Ada", "ada@example.com");
        students.create("Grace", "grace@example.com");
        students.create("Alan", "alan@example.com");
        service = new EventService(new InMemoryEventRepository(), students);
    }

    /** Valid details with the given title and invitees. */
    private static EventDetails details(String title, String... invitees) {
        return new EventDetails(title, List.of(invitees), DAY, DAY.plusDays(1), 60,
                Set.of(RoomFeature.WHITEBOARD), 2);
    }

    @Test
    @DisplayName("Given no events, when two drafts are created, then they get sequential IDs, DRAFT status, and PENDING responses")
    void createsDrafts() {
        Event first = service.create(details("Study session", "S001", "S002"));
        Event second = service.create(details("Club workshop", "S002", "S003"));

        assertEquals("E001", first.id());
        assertEquals("E002", second.id());
        assertEquals(EventStatus.DRAFT, first.status());
        assertEquals(AttendanceResponse.PENDING, first.responses().get("S001"));
        assertEquals(List.of(first, second), service.list());
    }

    @Test
    @DisplayName("Given drafts created out of date order, when events are listed, then they are sorted by first day and then by ID")
    void listsByFirstDayThenId() {
        Event later = service.create(new EventDetails("Later", List.of("S001", "S002"),
                DAY.plusDays(3), DAY.plusDays(3), 60, Set.of(), 2));
        Event earlyA = service.create(details("Early A", "S001", "S002"));
        Event earlyB = service.create(details("Early B", "S002", "S003"));

        assertEquals(List.of(earlyA, earlyB, later), service.list());
    }

    @Test
    @DisplayName("Given a student ID that does not exist, when a draft is created, then it is rejected, nothing is saved, and no ID is used up")
    void rejectsUnknownInvitee() {
        assertThrows(ValidationException.class, () -> service.create(details("Meeting", "S001", "S999")));

        assertEquals(List.of(), service.list());
        assertEquals("E001", service.create(details("Meeting", "S001", "S002")).id());
    }

    @Test
    @DisplayName("Given a draft, when its details are edited, then the ID is kept and the new details are stored")
    void editsDraft() {
        Event event = service.create(details("Meeting", "S001", "S002"));

        Event edited = service.edit(event.id(), details("Renamed meeting", "S001", "S002", "S003"));

        assertEquals(event.id(), edited.id());
        assertEquals("Renamed meeting", service.get(event.id()).details().title());
        assertEquals(3, service.get(event.id()).responses().size());
    }

    @Test
    @DisplayName("Given a draft, when it is edited to invite an unknown student, then it is rejected and unchanged")
    void rejectsEditWithUnknownInvitee() {
        Event event = service.create(details("Meeting", "S001", "S002"));

        assertThrows(ValidationException.class, () -> service.edit(event.id(), details("Meeting", "S001", "S999")));
        assertEquals(event, service.get(event.id()));
    }

    @Test
    @DisplayName("Given a draft, when viewed with a padded ID, then the event is found")
    void getTrimsId() {
        Event event = service.create(details("Meeting", "S001", "S002"));

        assertEquals(event, service.get("  " + event.id() + " "));
    }

    @Test
    @DisplayName("Given no event with an ID, when it is viewed or edited, then it is rejected")
    void rejectsUnknownEvent() {
        assertThrows(ValidationException.class, () -> service.get("E999"));
        assertThrows(ValidationException.class, () -> service.edit("E999", details("Meeting", "S001", "S002")));
    }
}
