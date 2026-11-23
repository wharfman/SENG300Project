/*
 * RoomFreeRuleTest.java
 *
 * Unit tests for RoomFreeRule: back-to-back bookings in the same room are
 * allowed, and any actual overlap is rejected.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.AttendanceResponse;
import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.Room;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.campusplan.application.scheduling.SchedulingFixtures.DAY;
import static com.campusplan.application.scheduling.SchedulingFixtures.draft;
import static com.campusplan.application.scheduling.SchedulingFixtures.scheduled;
import static com.campusplan.application.scheduling.SchedulingFixtures.slot;
import static com.campusplan.application.scheduling.SchedulingFixtures.withEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Candidate times compared with scheduled bookings of the same room. */
class RoomFreeRuleTest {

    /** Rule under test. */
    private final RoomFreeRule rule = new RoomFreeRule();

    /** The candidate room. */
    private final Room room = new Room("R01", "Lab", 6, Set.of());

    /** R01 booked 13:00-14:00 by E002. */
    private final Event booked = scheduled("E002", "R01", slot("13:00", "14:00"),
            Map.of("S101", AttendanceResponse.PENDING, "S102", AttendanceResponse.PENDING));

    @ParameterizedTest
    @CsvSource({"12:00, 13:00", "14:00, 15:00"})
    @DisplayName("Given the room booked 13:00-14:00, when an adjacent time is checked, then it passes")
    void allowsAdjacentBookings(String start, String end) {
        assertEquals(Optional.empty(), rule.check(draft(2, Set.of()), room, slot(start, end), withEvents(booked)));
    }

    @ParameterizedTest
    @CsvSource({"13:00, 14:00", "13:30, 14:30", "12:30, 13:30", "12:00, 15:00"})
    @DisplayName("Given the room booked 13:00-14:00, when an overlapping time is checked, then it fails naming the booking")
    void rejectsOverlappingBooking(String start, String end) {
        assertEquals(Optional.of("room booked for E002 at 13:00-14:00"),
                rule.check(draft(2, Set.of()), room, slot(start, end), withEvents(booked)));
    }

    @Test
    @DisplayName("Given a different room or a different date booked at the same time, when checked, then it passes")
    void ignoresOtherRoomsAndDates() {
        Event otherRoom = scheduled("E003", "R02", slot("13:00", "14:00"),
                Map.of("S101", AttendanceResponse.PENDING, "S102", AttendanceResponse.PENDING));
        Event otherDay = scheduled("E004", "R01",
                new AvailabilityInterval(DAY.plusDays(1), LocalTime.of(13, 0), LocalTime.of(14, 0)),
                Map.of("S101", AttendanceResponse.PENDING, "S102", AttendanceResponse.PENDING));

        assertEquals(Optional.empty(), rule.check(draft(2, Set.of()), room, slot("13:00", "14:00"),
                withEvents(otherRoom, otherDay)));
    }
}
