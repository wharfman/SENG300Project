package com.campusplan.application;

import com.campusplan.domain.Room;
import com.campusplan.domain.RoomFeature;
import com.campusplan.persistence.JsonRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.campusplan.domain.RoomFeature.ACCESSIBLE;
import static com.campusplan.domain.RoomFeature.PROJECTOR;
import static com.campusplan.domain.RoomFeature.WHITEBOARD;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** RoomService over the real seed catalogue loaded through JsonRoomRepository. */
class RoomCatalogueIntegrationTest {
    private final RoomService service = new RoomService(JsonRoomRepository.fromResource("/seed/rooms.json"));

    private static List<String> ids(List<Room> rooms) {
        return rooms.stream().map(Room::id).toList();
    }

    @Test
    @DisplayName("Given the seed catalogue, when listing rooms, then every seeded room is returned in ID order")
    void listsSeedCatalogue() {
        assertEquals(List.of("R01", "R02", "R03", "R04", "R05", "R06", "R07"), ids(service.list()));
    }

    @Test
    @DisplayName("Given the seed catalogue, when filtering for six seats and a whiteboard, then only qualifying rooms are returned")
    void filtersSeedByCapacityAndFeature() {
        assertEquals(List.of("R01", "R02", "R04", "R06"), ids(service.filter(6, Set.of(WHITEBOARD))));
    }

    @Test
    @DisplayName("Given the seed catalogue, when filtering for every feature, then only rooms with all three are returned")
    void filtersSeedByAllFeatures() {
        assertEquals(List.of("R02"), ids(service.filter(1, Set.of(WHITEBOARD, PROJECTOR, ACCESSIBLE))));
    }

    @Test
    @DisplayName("Given the seed catalogue, when filtering above the largest capacity, then nothing is returned")
    void filtersSeedAboveLargestRoom() {
        assertEquals(List.of(), service.filter(41, Set.<RoomFeature>of()));
    }
}
