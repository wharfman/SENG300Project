/*
 * RoomServiceTest.java
 *
 * Unit tests for RoomService listing order and capacity/feature filtering,
 * using a small fixed catalogue.
 */
package com.campusplan.application;

import com.campusplan.domain.Room;
import com.campusplan.domain.RoomFeature;
import com.campusplan.domain.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.campusplan.domain.RoomFeature.ACCESSIBLE;
import static com.campusplan.domain.RoomFeature.PROJECTOR;
import static com.campusplan.domain.RoomFeature.WHITEBOARD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link RoomService} listing and filtering over a three-room catalogue. */
class RoomServiceTest {
    private final Room small = new Room("R01", "Small", 4, Set.of(WHITEBOARD));
    private final Room medium = new Room("R02", "Medium", 6, Set.of(WHITEBOARD, PROJECTOR));
    private final Room large = new Room("R03", "Large", 20, Set.of(ACCESSIBLE));
    // The catalogue is deliberately out of ID order to show that results are sorted.
    private final RoomService service = new RoomService(() -> List.of(large, small, medium));

    @Test
    @DisplayName("Given a catalogue, when listing rooms, then all are shown ordered by ID")
    void listsAllById() {
        assertEquals(List.of(small, medium, large), service.list());
    }

    @Test
    @DisplayName("Given a minimum capacity, when filtering, then only rooms seating at least that many are returned")
    void filtersByCapacity() {
        assertEquals(List.of(medium, large), service.filter(6, Set.<RoomFeature>of()));
    }

    @Test
    @DisplayName("Given required features, when filtering, then only rooms with all of them are returned")
    void filtersByFeatures() {
        assertEquals(List.of(small, medium), service.filter(1, Set.of(WHITEBOARD)));
        assertEquals(List.of(medium), service.filter(1, Set.of(WHITEBOARD, PROJECTOR)));
    }

    @Test
    @DisplayName("Given capacity and features no room meets, when filtering, then nothing is returned")
    void filterWithNoMatch() {
        assertEquals(List.of(), service.filter(10, Set.of(WHITEBOARD)));
    }

    @Test
    @DisplayName("Given a minimum capacity below 1, when filtering, then it is rejected")
    void rejectsInvalidCapacity() {
        assertThrows(ValidationException.class, () -> service.filter(0, Set.of()));
    }
}
