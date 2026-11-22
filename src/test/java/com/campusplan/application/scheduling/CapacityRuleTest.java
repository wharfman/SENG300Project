/*
 * CapacityRuleTest.java
 *
 * Unit tests for CapacityRule: a room must seat every invitee, at and just
 * below the boundary.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.Event;
import com.campusplan.domain.Room;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static com.campusplan.application.scheduling.SchedulingFixtures.draft;
import static com.campusplan.application.scheduling.SchedulingFixtures.slot;
import static com.campusplan.application.scheduling.SchedulingFixtures.withEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Room capacity compared with the number of invitees. */
class CapacityRuleTest {

    /** Rule under test. */
    private final CapacityRule rule = new CapacityRule();

    @Test
    @DisplayName("Given a room seating exactly the number of invitees, when checked, then it passes")
    void acceptsCapacityEqualToInvitees() {
        Event sixInvitees = draft(6, Set.of());

        assertEquals(Optional.empty(), rule.check(sixInvitees, new Room("R01", "Lab", 6, Set.of()),
                slot("13:00", "14:00"), withEvents()));
    }

    @Test
    @DisplayName("Given a room holding five and an event inviting six, when checked, then it fails with both numbers")
    void rejectsRoomOneSeatShort() {
        Event sixInvitees = draft(6, Set.of());

        assertEquals(Optional.of("room holds 5, event invites 6"), rule.check(sixInvitees,
                new Room("R01", "Lab", 5, Set.of()), slot("13:00", "14:00"), withEvents()));
    }
}
