/*
 * RoomFreeRule.java
 *
 * Application-layer room rule: the room must have no overlapping SCHEDULED event.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.Room;

import java.util.Optional;

/** Fails a room already reserved for an overlapping scheduled event. Back-to-back bookings are allowed. */
public class RoomFreeRule implements RoomRule {

    /** {@inheritDoc} */
    @Override
    public Optional<String> check(Event event, Room room, AvailabilityInterval slot, Schedule schedule) {
        return schedule.scheduledOverlapping(slot).stream()
                .filter(booked -> booked.roomId().equals(room.id()))
                .findFirst()
                .map(booked -> "room booked for " + booked.id() + " at "
                        + booked.slot().start() + "-" + booked.slot().end());
    }
}
