/*
 * CapacityRule.java
 *
 * Application-layer room rule: the room must seat every invitee.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.Room;

import java.util.Optional;

/** Fails a room that cannot seat every invitee, however many of them are eligible. */
public class CapacityRule implements RoomRule {

    /** {@inheritDoc} */
    @Override
    public Optional<String> check(Event event, Room room, AvailabilityInterval slot, Schedule schedule) {
        int invitees = event.details().inviteeIds().size();
        if (room.capacity() >= invitees) {
            return Optional.empty();
        }
        return Optional.of("room holds " + room.capacity() + ", event invites " + invitees);
    }
}
