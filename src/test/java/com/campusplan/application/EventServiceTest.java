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
import com.campusplan.domain.AvailabilityInterval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link EventService} operations over in-memory storage, including rejected operations. */
class EventServiceTest {

    /** First day of the date window. */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    /** Service under test, recreated empty for each test. */
    private EventService service;
    private InMemoryEventRepository repository;

    /** Starts each test with three students, S001-S003, and no events. */
    @BeforeEach
    void setUp() {
        StudentService students = new StudentService(new InMemoryStudentRepository());
        students.create("Ada", "ada@example.com");
        students.create("Grace", "grace@example.com");
        students.create("Alan", "alan@example.com");
        repository = new InMemoryEventRepository();
        service = new EventService(repository, students);
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
    @DisplayName("Given existing events, when no filters are selected, then all events are returned")
    void listsWithoutFilters() {
        Event first = service.create(details("Meeting", "S001", "S002"));
        Event second = service.create(details("Workshop", "S002", "S003"));

        assertEquals(List.of(first, second), service.list(null, null));
    }

    @Test
    @DisplayName("Given events with different date windows, when filtered by date, then only matching events are returned")
    void filtersByDate() {
        Event matching = service.create(details("Meeting", "S001", "S002"));

        service.create(new EventDetails("Later", List.of("S001", "S002"),
            DAY.plusDays(3), DAY.plusDays(3), 60, Set.of(), 2));

        assertEquals(List.of(matching), service.list(DAY, null));
    }

    @Test
    @DisplayName("Given an event date window, when filtered by date, then both boundaries are included")
    void includesDateWindowBoundaries() {
        Event event = service.create(new EventDetails(
            "Meeting", List.of("S001", "S002"),
            DAY, DAY.plusDays(2), 60, Set.of(), 2));

        assertEquals(List.of(event), service.list(DAY, null));
        assertEquals(List.of(event), service.list(DAY.plusDays(1), null));
        assertEquals(List.of(event), service.list(DAY.plusDays(2), null));
        assertTrue(service.list(DAY.minusDays(1), null).isEmpty());
        assertTrue(service.list(DAY.plusDays(3), null).isEmpty());
    }

    @Test
    @DisplayName("Given a draft event, when filtered by status, then only matching statuses are returned")
    void filtersByStatus() {
        Event event = service.create(details("Meeting", "S001", "S002"));

        assertEquals(List.of(event), service.list(null, EventStatus.DRAFT));
        assertTrue(service.list(null, EventStatus.SCHEDULED).isEmpty());
        assertTrue(service.list(null, EventStatus.CANCELLED).isEmpty());
    }

    @Test
    @DisplayName("Given date and status filters, when listing events, then both conditions must match")
    void filtersByDateAndStatus() {
        Event event = service.create(details("Meeting", "S001", "S002"));

        assertEquals(List.of(event), service.list(DAY, EventStatus.DRAFT));
        assertTrue(service.list(DAY, EventStatus.SCHEDULED).isEmpty());
        assertTrue(service.list(DAY.minusDays(1), EventStatus.DRAFT).isEmpty());
    }

    @Test
    @DisplayName("Given a scheduled event, when filtered by date, then only its scheduled date matches")
    void filtersScheduledEventByActualDate() {
        Event draft = service.create(details("Meeting", "S001", "S002"));

        Event scheduled = draft.schedule("R001", new AvailabilityInterval(
            DAY.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0)));

        repository.save(scheduled);

        assertEquals(List.of(scheduled),
            service.list(DAY.plusDays(1), EventStatus.SCHEDULED));

        assertTrue(service.list(DAY, EventStatus.SCHEDULED).isEmpty());
        assertTrue(service.list(null, EventStatus.DRAFT).isEmpty());
    }

    @Test
    @DisplayName("Given a cancelled event, when filtered by date, then its original date window is used")
    void filtersCancelledEventByDateWindow() {
        Event draft = service.create(details("Meeting", "S001", "S002"));

        Event scheduled = draft.schedule("R001", new AvailabilityInterval(
            DAY.plusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0)));

        Event cancelled = scheduled.cancel();
        repository.save(cancelled);

        assertEquals(List.of(cancelled), service.list(DAY, EventStatus.CANCELLED));
        assertEquals(List.of(cancelled), service.list(DAY.plusDays(1), EventStatus.CANCELLED));

        assertTrue(service.list(DAY.plusDays(2), EventStatus.CANCELLED).isEmpty());
        assertTrue(service.list(null, EventStatus.SCHEDULED).isEmpty());
    }

    @Test
    @DisplayName("Given events created out of order, when filtered, then date and ID ordering is preserved")
    void preservesSortingWithFilters() {
        Event later = service.create(new EventDetails(
            "Later", List.of("S001", "S002"),
            DAY.plusDays(3), DAY.plusDays(3), 60, Set.of(), 2));

        Event earlyA = service.create(details("Early A", "S001", "S002"));
        Event earlyB = service.create(details("Early B", "S002", "S003"));

        assertEquals(List.of(earlyA, earlyB, later),
            service.list(null, EventStatus.DRAFT));
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
    @DisplayName("Given a draft for 3 with minimum 3, when an invitee is removed, then it is rejected because the minimum no longer fits, and the draft is unchanged")
    void rejectsEditBelowMinimumAttendance() {
        Event event = service.create(new EventDetails("Meeting", List.of("S001", "S002", "S003"),
                DAY, DAY, 60, Set.of(), 3));

        assertThrows(ValidationException.class, () -> service.edit(event.id(), new EventDetails("Meeting",
                List.of("S001", "S002"), DAY, DAY, 60, Set.of(), 3)));
        assertEquals(event, service.get(event.id()));
    }

    @Test
    @DisplayName("Given a draft for 20, when a 21st invitee is added, then it is rejected and the draft is unchanged")
    void rejectsEditTo21Invitees() {
        StudentService students = new StudentService(new InMemoryStudentRepository());
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            ids.add(students.create("Student " + i, "s" + i + "@example.com").id());
        }
        EventService big = new EventService(new InMemoryEventRepository(), students);
        Event event = big.create(new EventDetails("Assembly", ids.subList(0, 20), DAY, DAY, 60, Set.of(), 2));

        assertThrows(ValidationException.class,
                () -> big.edit(event.id(), new EventDetails("Assembly", ids, DAY, DAY, 60, Set.of(), 2)));
        assertEquals(event, big.get(event.id()));
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
