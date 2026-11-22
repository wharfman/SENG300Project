/*
 * NoConflictRuleTest.java
 *
 * Unit tests for NoConflictRule: an ACCEPTED overlapping scheduled event makes
 * an invitee ineligible; adjacent events and other responses do not.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.AttendanceResponse;
import com.campusplan.domain.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Optional;

import static com.campusplan.application.scheduling.SchedulingFixtures.scheduled;
import static com.campusplan.application.scheduling.SchedulingFixtures.slot;
import static com.campusplan.application.scheduling.SchedulingFixtures.withEvents;
import static com.campusplan.domain.AttendanceResponse.ACCEPTED;
import static com.campusplan.domain.AttendanceResponse.PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Candidate times compared with the invitee's responses to other scheduled events. */
class NoConflictRuleTest {

    /** Rule under test. */
    private final NoConflictRule rule = new NoConflictRule();

    /** E002 in R02 13:00-14:00, which S001 has accepted. */
    private final Event acceptedElsewhere = scheduled("E002", "R02", slot("13:00", "14:00"),
            Map.of("S001", ACCEPTED, "S101", PENDING));

    @Test
    @DisplayName("Given a student who accepted 13:00-14:00 elsewhere, when an overlapping time is checked, then they are ineligible naming that event")
    void rejectsOverlappingAcceptedEvent() {
        assertEquals(Optional.of("already accepted E002"),
                rule.check("S001", slot("13:30", "14:30"), withEvents(acceptedElsewhere)));
    }

    @Test
    @DisplayName("Given a student who accepted 13:00-14:00 elsewhere, when the adjacent 14:00-15:00 is checked, then they are eligible")
    void allowsAdjacentAcceptedEvent() {
        assertEquals(Optional.empty(), rule.check("S001", slot("14:00", "15:00"), withEvents(acceptedElsewhere)));
    }

    @ParameterizedTest
    @EnumSource(value = AttendanceResponse.class, names = {"PENDING", "DECLINED"})
    @DisplayName("Given a PENDING or DECLINED response to an overlapping scheduled event, when checked, then the student is eligible")
    void ignoresResponsesThatReserveNoTime(AttendanceResponse response) {
        Event overlapping = scheduled("E002", "R02", slot("13:00", "14:00"), Map.of("S001", response, "S101", PENDING));

        assertEquals(Optional.empty(), rule.check("S001", slot("13:00", "14:00"), withEvents(overlapping)));
    }

    @Test
    @DisplayName("Given a student who accepted two overlapping events, when checked, then the reason names both")
    void namesEveryConflictingEvent() {
        Event alsoAccepted = scheduled("E003", "R03", slot("13:30", "14:30"), Map.of("S001", ACCEPTED, "S101", PENDING));

        assertEquals(Optional.of("already accepted E002, E003"),
                rule.check("S001", slot("13:00", "14:00"), withEvents(acceptedElsewhere, alsoAccepted)));
    }

    @Test
    @DisplayName("Given an overlapping scheduled event the student is not invited to, when checked, then they are eligible")
    void ignoresEventsStudentIsNotInvitedTo() {
        assertEquals(Optional.empty(), rule.check("S002", slot("13:00", "14:00"), withEvents(acceptedElsewhere)));
    }
}
