/*
 * SchedulingServiceCancelTest.java
 *
 * Unit tests for SchedulingService.cancel: cancelling a scheduled event frees its
 * room and its invitees' time, keeps the record, and cannot be undone by scheduling again.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.Availability;
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.campusplan.application.scheduling.SchedulingFixtures.DAY;
import static com.campusplan.application.scheduling.SchedulingFixtures.scheduled;
import static com.campusplan.application.scheduling.SchedulingFixtures.slot;
import static com.campusplan.domain.AttendanceResponse.ACCEPTED;
import static com.campusplan.domain.AttendanceResponse.DECLINED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link SchedulingService#cancel} over in-memory storage. */
class SchedulingServiceCancelTest {

    /** Event storage shared by the service and the test. */
    private final InMemoryEventRepository events = new InMemoryEventRepository();

    /** Availability storage shared by the service and the test. */
    private final InMemoryAvailabilityRepository availability = new InMemoryAvailabilityRepository();

    /** Service under test, over one room that seats six. */
    private final SchedulingService service = new SchedulingService(events,
            () -> List.of(new Room("R01", "Lab", 6, Set.of())), availability);

    /** E101 in R01 13:00-14:00, which S001 accepted and S002 declined. */
    private final Event booked = scheduled("E101", "R01", slot("13:00", "14:00"),
            Map.of("S001", ACCEPTED, "S002", DECLINED));

    /**
     * Stores E101, and draft E001 (S001 and S003, minimum 2) for the same day with both
     * invitees free 13:00-14:00 only. While E101 is scheduled, E001 has no possible time.
     */
    @BeforeEach
    void setUp() {
        events.save(booked);
        events.save(Event.draft("E001", new EventDetails("Study group", List.of("S001", "S003"), DAY, DAY, 60, Set.of(), 2)));
        for (String student : List.of("S001", "S003")) {
            availability.save(new Availability(student, List.of(slot("13:00", "14:00"))));
        }
    }

    /**
     * Cancels and expects a rejection that leaves every stored event as it was.
     *
     * @param eventId the event to cancel
     * @return the rejection message
     */
    private String rejectedWithoutChange(String eventId) {
        List<Event> before = events.findAll();
        ValidationException e = assertThrows(ValidationException.class, () -> service.cancel(eventId));
        assertEquals(before, events.findAll());
        return e.getMessage();
    }

    @Test
    @DisplayName("Given a scheduled event, when it is cancelled, then it is stored as CANCELLED with no room or time and its responses kept")
    void cancelKeepsRecordAndResponses() {
        Event cancelled = service.cancel(" E101 ");

        assertEquals(EventStatus.CANCELLED, cancelled.status());
        assertEquals(null, cancelled.roomId());
        assertEquals(null, cancelled.slot());
        assertEquals(Map.of("S001", ACCEPTED, "S002", DECLINED), cancelled.responses());
        assertEquals(cancelled, events.findById("E101").orElseThrow());
    }

    @Test
    @DisplayName("Given a room and an accepted student held by a scheduled event, when it is cancelled, then both are free again for another event")
    void cancelFreesRoomAndAcceptedTime() {
        assertEquals(List.of(), service.suggest("E001").top());

        service.cancel("E101");
        SuggestionResult after = service.suggest("E001");

        assertEquals(1, after.top().size());
        assertEquals("R01", after.top().get(0).roomId());
        assertEquals(slot("13:00", "14:00"), after.top().get(0).slot());
        assertEquals(List.of("S001", "S003"), after.top().get(0).eligible());
    }

    @Test
    @DisplayName("Given a cancelled event, when a suggestion is confirmed for it, then it is rejected because it cannot be scheduled again")
    void cancelledEventCannotBeScheduledAgain() {
        service.cancel("E101");
        Suggestion sameRoomAndTime = new Suggestion("R01", slot("13:00", "14:00"), List.of("S001"), Map.of());

        ValidationException e = assertThrows(ValidationException.class, () -> service.confirm("E101", sameRoomAndTime));

        assertEquals("Event E101 is CANCELLED and cannot be scheduled again.", e.getMessage());
        assertEquals(EventStatus.CANCELLED, events.findById("E101").orElseThrow().status());
    }

    @Test
    @DisplayName("Given a draft, an already cancelled event, or an unknown ID, when cancelled, then it is rejected and nothing changes")
    void rejectsEventsThatAreNotScheduled() {
        service.cancel("E101");

        assertEquals("Only SCHEDULED events can be cancelled; event E001 is DRAFT.", rejectedWithoutChange("E001"));
        assertEquals("Only SCHEDULED events can be cancelled; event E101 is CANCELLED.", rejectedWithoutChange("E101"));
        assertEquals("No event with ID E404.", rejectedWithoutChange("E404"));
    }
}
