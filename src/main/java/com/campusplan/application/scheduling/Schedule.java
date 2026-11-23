/*
 * Schedule.java
 *
 * Application-layer snapshot of the existing events and availability that the
 * scheduling rules check each candidate against.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.Availability;
import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.EventStatus;

import java.util.List;
import java.util.Map;

/**
 * What is already planned when suggestions are generated. Read-only: rules look things up
 * here and never change it.
 *
 * @param events       every stored event, in any status
 * @param availability each student's free time, keyed by student ID
 */
public record Schedule(List<Event> events, Map<String, Availability> availability) {

    /** Copies both collections so the snapshot cannot change under the rules. */
    public Schedule {
        events = List.copyOf(events);
        availability = Map.copyOf(availability);
    }

    /**
     * A student's free time. A student with nothing recorded is never available.
     *
     * @param studentId ID of the student
     * @return their availability, empty if none is recorded
     */
    public Availability availabilityOf(String studentId) {
        return availability.getOrDefault(studentId, Availability.empty(studentId));
    }

    /**
     * SCHEDULED events whose time shares at least one minute with the slot. Adjacent events
     * do not overlap, and only SCHEDULED events have a time.
     *
     * @param slot the candidate time
     * @return the overlapping scheduled events, in stored order
     */
    public List<Event> scheduledOverlapping(AvailabilityInterval slot) {
        return events.stream()
                .filter(e -> e.status() == EventStatus.SCHEDULED && e.slot().overlaps(slot))
                .toList();
    }
}
