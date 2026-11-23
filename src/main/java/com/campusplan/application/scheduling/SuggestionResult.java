/*
 * SuggestionResult.java
 *
 * Application-layer result record: the ranked suggestions for an event and how
 * many candidates were rejected, and for what.
 */
package com.campusplan.application.scheduling;

import java.util.List;

/**
 * The outcome of a suggestion search. Every candidate is counted exactly once: as a
 * suggestion (valid), a room rejection, or an attendance rejection.
 *
 * @param top                   up to five suggestions, best first; empty when none qualify
 * @param rejectedForRoom       candidates whose room failed a room rule
 * @param rejectedForAttendance candidates whose room passed but with too few eligible invitees
 */
public record SuggestionResult(List<Suggestion> top, int rejectedForRoom, int rejectedForAttendance) {

    /** Copies the suggestion list. */
    public SuggestionResult {
        top = List.copyOf(top);
    }
}
