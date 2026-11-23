/*
 * Suggestion.java
 *
 * Application-layer result record: one room and time that could host an event,
 * with who could attend and why the others cannot.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.AvailabilityInterval;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A valid candidate for an event. Showing a suggestion reserves nothing.
 *
 * @param roomId            the candidate room
 * @param slot              the candidate date and time
 * @param eligible          IDs of invitees who can attend, in invitee order
 * @param ineligibleReasons why each other invitee cannot attend, keyed by student ID, in invitee order
 */
public record Suggestion(String roomId, AvailabilityInterval slot, List<String> eligible,
                         Map<String, String> ineligibleReasons) {

    /** Copies both collections, keeping their order. */
    public Suggestion {
        eligible = List.copyOf(eligible);
        ineligibleReasons = Collections.unmodifiableMap(new LinkedHashMap<>(ineligibleReasons));
    }

    /**
     * Number of invitees who can attend.
     *
     * @return the size of {@link #eligible()}
     */
    public int eligibleCount() {
        return eligible.size();
    }
}
