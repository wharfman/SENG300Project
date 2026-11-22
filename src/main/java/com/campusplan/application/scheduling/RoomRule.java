/*
 * RoomRule.java
 *
 * Application-layer strategy interface for one check a room must pass before a
 * scheduling candidate is considered.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.Room;

import java.util.Optional;

/** A rule a room must meet for an event at a given time. Rules are independent of each other. */
public interface RoomRule {

    /**
     * Checks one room for one candidate time.
     *
     * @param event    the event being planned
     * @param room     the candidate room
     * @param slot     the candidate date and time
     * @param schedule what is already planned
     * @return the reason the room fails, or empty if it passes
     */
    Optional<String> check(Event event, Room room, AvailabilityInterval slot, Schedule schedule);
}
