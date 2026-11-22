/*
 * FeatureRuleTest.java
 *
 * Unit tests for FeatureRule: a room must provide every required feature,
 * and the reason names each missing one.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.Room;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static com.campusplan.application.scheduling.SchedulingFixtures.draft;
import static com.campusplan.application.scheduling.SchedulingFixtures.slot;
import static com.campusplan.application.scheduling.SchedulingFixtures.withEvents;
import static com.campusplan.domain.RoomFeature.ACCESSIBLE;
import static com.campusplan.domain.RoomFeature.PROJECTOR;
import static com.campusplan.domain.RoomFeature.WHITEBOARD;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Required features compared with the features a room provides. */
class FeatureRuleTest {

    /** Rule under test. */
    private final FeatureRule rule = new FeatureRule();

    @Test
    @DisplayName("Given a room with every required feature and more, when checked, then it passes")
    void acceptsRoomWithAllRequiredFeatures() {
        Room room = new Room("R01", "Lab", 6, Set.of(WHITEBOARD, PROJECTOR, ACCESSIBLE));

        assertEquals(Optional.empty(), rule.check(draft(2, Set.of(WHITEBOARD, PROJECTOR)), room,
                slot("13:00", "14:00"), withEvents()));
    }

    @Test
    @DisplayName("Given an event requiring no features, when a room with none is checked, then it passes")
    void acceptsAnyRoomWhenNothingRequired() {
        assertEquals(Optional.empty(), rule.check(draft(2, Set.of()), new Room("R01", "Lab", 6, Set.of()),
                slot("13:00", "14:00"), withEvents()));
    }

    @Test
    @DisplayName("Given a room missing one or two required features, when checked, then it fails naming each missing feature")
    void rejectsRoomMissingFeatures() {
        Room whiteboardOnly = new Room("R01", "Lab", 6, Set.of(WHITEBOARD));
        Room bare = new Room("R02", "Den", 6, Set.of());

        assertEquals(Optional.of("room lacks PROJECTOR"), rule.check(draft(2, Set.of(WHITEBOARD, PROJECTOR)),
                whiteboardOnly, slot("13:00", "14:00"), withEvents()));
        assertEquals(Optional.of("room lacks WHITEBOARD, PROJECTOR"), rule.check(draft(2, Set.of(WHITEBOARD, PROJECTOR)),
                bare, slot("13:00", "14:00"), withEvents()));
    }
}
