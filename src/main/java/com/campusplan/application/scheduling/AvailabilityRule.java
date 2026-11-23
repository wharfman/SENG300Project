/*
 * AvailabilityRule.java
 *
 * Application-layer invitee rule: the invitee's availability must cover the whole candidate time.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.AvailabilityInterval;

import java.util.Optional;

/** Makes an invitee ineligible unless they are free for the entire candidate time. */
public class AvailabilityRule implements InviteeRule {

    /** {@inheritDoc} */
    @Override
    public Optional<String> check(String studentId, AvailabilityInterval slot, Schedule schedule) {
        if (schedule.availabilityOf(studentId).isAvailable(slot.date(), slot.start(), slot.end())) {
            return Optional.empty();
        }
        return Optional.of("not available " + slot.start() + "-" + slot.end());
    }
}
