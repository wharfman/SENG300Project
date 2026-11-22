/*
 * AvailabilityRuleTest.java
 *
 * Unit tests for AvailabilityRule: an invitee is eligible only when their
 * availability covers the whole candidate time.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.Availability;
import com.campusplan.domain.AvailabilityInterval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.campusplan.application.scheduling.SchedulingFixtures.DAY;
import static com.campusplan.application.scheduling.SchedulingFixtures.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Candidate times compared with an invitee's recorded free time. */
class AvailabilityRuleTest {

    /** Rule under test. */
    private final AvailabilityRule rule = new AvailabilityRule();

    /** S001 is free 13:00-15:00 on {@link SchedulingFixtures#DAY}. */
    private final Schedule freeAfternoon = new Schedule(List.of(),
            Map.of("S001", new Availability("S001", List.of(slot("13:00", "15:00")))));

    @ParameterizedTest
    @CsvSource({"13:00, 15:00", "13:00, 14:00", "14:00, 15:00"})
    @DisplayName("Given an invitee free 13:00-15:00, when a time inside or exactly matching it is checked, then they are eligible")
    void acceptsCoveredTime(String start, String end) {
        assertEquals(Optional.empty(), rule.check("S001", slot(start, end), freeAfternoon));
    }

    @ParameterizedTest
    @CsvSource({"12:30, 13:30", "14:30, 15:30", "12:00, 16:00"})
    @DisplayName("Given an invitee free 13:00-15:00, when a time only partly covered is checked, then they are ineligible")
    void rejectsPartlyCoveredTime(String start, String end) {
        assertEquals(Optional.of("not available " + start + "-" + end), rule.check("S001", slot(start, end), freeAfternoon));
    }

    @Test
    @DisplayName("Given no availability on the date or none at all, when checked, then the invitee is ineligible")
    void rejectsMissingAvailability() {
        AvailabilityInterval nextDay = new AvailabilityInterval(DAY.plusDays(1), LocalTime.of(13, 0), LocalTime.of(14, 0));

        assertEquals(Optional.of("not available 13:00-14:00"), rule.check("S001", nextDay, freeAfternoon));
        assertEquals(Optional.of("not available 13:00-14:00"), rule.check("S002", slot("13:00", "14:00"), freeAfternoon));
    }
}
