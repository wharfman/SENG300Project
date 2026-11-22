/*
 * NoConflictRule.java
 *
 * Application-layer invitee rule: the invitee must not have ACCEPTED another
 * SCHEDULED event that overlaps the candidate time.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.AttendanceResponse;
import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;

import java.util.List;
import java.util.Optional;

/**
 * Makes an invitee ineligible when they have accepted an overlapping scheduled event,
 * naming every such event. PENDING and DECLINED responses reserve no time.
 */
public class NoConflictRule implements InviteeRule {

    /** {@inheritDoc} */
    @Override
    public Optional<String> check(String studentId, AvailabilityInterval slot, Schedule schedule) {
        List<String> accepted = schedule.scheduledOverlapping(slot).stream()
                .filter(e -> e.responses().get(studentId) == AttendanceResponse.ACCEPTED)
                .map(Event::id)
                .toList();
        if (accepted.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("already accepted " + String.join(", ", accepted));
    }
}
