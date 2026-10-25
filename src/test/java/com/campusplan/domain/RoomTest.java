/*
 * RoomTest.java
 *
 * Unit tests for Room field validation and feature matching,
 * using boundary values and invalid-input partitions.
 */
package com.campusplan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static com.campusplan.domain.RoomFeature.ACCESSIBLE;
import static com.campusplan.domain.RoomFeature.PROJECTOR;
import static com.campusplan.domain.RoomFeature.WHITEBOARD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validation rules and feature matching of the {@link Room} record. */
class RoomTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    @DisplayName("Given a capacity at the 1-100 bounds, when a room is created, then it is accepted")
    void acceptsBoundaryCapacity(int capacity) {
        assertEquals(capacity, new Room("R01", "Lab", capacity, Set.of()).capacity());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 101})
    @DisplayName("Given a capacity outside 1-100, when a room is created, then it is rejected")
    void rejectsCapacityOutOfRange(int capacity) {
        assertThrows(ValidationException.class, () -> new Room("R01", "Lab", capacity, Set.of()));
    }

    @Test
    @DisplayName("Given a name that is blank or over 60 characters after trimming, when a room is created, then it is rejected")
    void rejectsInvalidName() {
        assertThrows(ValidationException.class, () -> new Room("R01", null, 5, Set.of()));
        assertThrows(ValidationException.class, () -> new Room("R01", "   ", 5, Set.of()));
        assertThrows(ValidationException.class, () -> new Room("R01", "a".repeat(61), 5, Set.of()));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 60})
    @DisplayName("Given a name of 1 or 60 characters after trimming, when a room is created, then it is accepted trimmed")
    void acceptsBoundaryNameLength(int length) {
        String name = "a".repeat(length);

        assertEquals(name, new Room("R01", "  " + name + "  ", 5, Set.of()).name());
    }

    @Test
    @DisplayName("Given a blank ID, when a room is created, then it is rejected")
    void rejectsBlankId() {
        assertThrows(ValidationException.class, () -> new Room(null, "Lab", 5, Set.of()));
        assertThrows(ValidationException.class, () -> new Room("  ", "Lab", 5, Set.of()));
    }

    @Test
    @DisplayName("Given no feature list, when a room is created, then it has no features")
    void nullFeaturesMeansNone() {
        assertTrue(new Room("R01", "Lab", 5, null).features().isEmpty());
    }

    @Test
    @DisplayName("Given a room with some features, when checking required features, then only subsets match")
    void hasFeaturesChecksSubset() {
        Room room = new Room("R01", "Lab", 5, Set.of(WHITEBOARD, ACCESSIBLE));

        assertTrue(room.hasFeatures(Set.of()));
        assertTrue(room.hasFeatures(Set.of(WHITEBOARD, ACCESSIBLE)));
        assertFalse(room.hasFeatures(Set.of(WHITEBOARD, PROJECTOR)));
    }
}
