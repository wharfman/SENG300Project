/*
 * InviteeRule.java
 *
 * Application-layer strategy interface for one check an invitee must pass to
 * count as eligible for a scheduling candidate.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.AvailabilityInterval;

import java.util.Optional;

/** A rule an invitee must meet to be eligible at a given time. Rules are independent of each other. */
public interface InviteeRule {

    /**
     * Checks one invitee for one candidate time.
     *
     * @param studentId ID of the invitee
     * @param slot      the candidate date and time
     * @param schedule  what is already planned
     * @return the reason the invitee is ineligible, or empty if they are eligible
     */
    Optional<String> check(String studentId, AvailabilityInterval slot, Schedule schedule);
}
