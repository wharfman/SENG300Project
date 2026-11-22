/*
 * SchedulingFixtures.java
 *
 * Test helpers that build the events, slots, and schedules used by the
 * scheduling rule tests.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.AttendanceResponse;
import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.EventDetails;
import com.campusplan.domain.EventStatus;
import com.campusplan.domain.RoomFeature;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** Builders for scheduling test data. Not instantiable. */
final class SchedulingFixtures {

    /** The date every fixture uses unless a test needs another. */
    static final LocalDate DAY = LocalDate.of(2026, 11, 3);

    /** Prevents instantiation. */
    private SchedulingFixtures() {
    }

    /**
     * A slot on {@link #DAY}.
     *
     * @param start start time, such as {@code "13:00"}
     * @param end   end time, such as {@code "14:00"}
     * @return the slot
     */
    static AvailabilityInterval slot(String start, String end) {
        return new AvailabilityInterval(DAY, LocalTime.parse(start), LocalTime.parse(end));
    }

    /**
     * A one-day draft on {@link #DAY} inviting S001, S002, ... with minimum attendance 2.
     *
     * @param invitees number of invitees, 2-20
     * @param features features the room must provide
     * @return the draft
     */
    static Event draft(int invitees, Set<RoomFeature> features) {
        List<String> ids = IntStream.rangeClosed(1, invitees).mapToObj(i -> String.format("S%03d", i)).toList();
        return Event.draft("E001", new EventDetails("Meeting", ids, DAY, DAY, 60, features, 2));
    }

    /**
     * A SCHEDULED event whose invitees are exactly the students in {@code responses}.
     *
     * @param id        event ID
     * @param roomId    reserved room
     * @param slot      reserved time
     * @param responses each invitee's response; at least two invitees
     * @return the scheduled event
     */
    static Event scheduled(String id, String roomId, AvailabilityInterval slot,
                           Map<String, AttendanceResponse> responses) {
        EventDetails details = new EventDetails("Booked", List.copyOf(responses.keySet()),
                slot.date(), slot.date(), 60, Set.of(), 2);
        return new Event(id, details, EventStatus.SCHEDULED, responses, roomId, slot);
    }

    /**
     * A schedule holding only the given events and no availability.
     *
     * @param events the existing events
     * @return the schedule
     */
    static Schedule withEvents(Event... events) {
        return new Schedule(List.of(events), Map.of());
    }
}
