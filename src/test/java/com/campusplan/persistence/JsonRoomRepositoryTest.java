package com.campusplan.persistence;

import com.campusplan.domain.Room;
import com.campusplan.domain.RoomFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonRoomRepositoryTest {

    private static InputStream json(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Given the seed catalogue, when loaded, then it has at least 6 rooms with varied capacities and every feature")
    void loadsSeedCatalogue() {
        List<Room> rooms = JsonRoomRepository.fromResource("/seed/rooms.json").findAll();

        assertTrue(rooms.size() >= 6);
        assertTrue(rooms.stream().map(Room::capacity).distinct().count() > 1);
        for (RoomFeature feature : RoomFeature.values()) {
            assertTrue(rooms.stream().anyMatch(r -> r.features().contains(feature)));
        }
    }

    @Test
    @DisplayName("Given a valid JSON room, when loaded, then its fields are read")
    void readsFields() {
        List<Room> rooms = new JsonRoomRepository(json(
                "[{\"id\":\"R01\",\"name\":\"Lab\",\"capacity\":6,\"features\":[\"WHITEBOARD\"]}]")).findAll();

        assertEquals(List.of(new Room("R01", "Lab", 6, Set.of(RoomFeature.WHITEBOARD))), rooms);
    }

    @Test
    @DisplayName("Given two rooms whose names differ only in case, when loaded, then the catalogue is rejected")
    void rejectsDuplicateNameIgnoringCase() {
        assertThrows(StorageException.class, () -> new JsonRoomRepository(json(
                "[{\"id\":\"R01\",\"name\":\"Lab\",\"capacity\":6},"
                        + "{\"id\":\"R02\",\"name\":\"LAB\",\"capacity\":8}]")));
    }

    @Test
    @DisplayName("Given two rooms with the same ID, when loaded, then the catalogue is rejected")
    void rejectsDuplicateId() {
        assertThrows(StorageException.class, () -> new JsonRoomRepository(json(
                "[{\"id\":\"R01\",\"name\":\"Lab\",\"capacity\":6},"
                        + "{\"id\":\"R01\",\"name\":\"Other\",\"capacity\":8}]")));
    }

    @Test
    @DisplayName("Given a room with an invalid capacity or unknown feature, when loaded, then the catalogue is rejected")
    void rejectsInvalidRoom() {
        assertThrows(StorageException.class, () -> new JsonRoomRepository(json(
                "[{\"id\":\"R01\",\"name\":\"Lab\",\"capacity\":0}]")));
        assertThrows(StorageException.class, () -> new JsonRoomRepository(json(
                "[{\"id\":\"R01\",\"name\":\"Lab\",\"capacity\":6,\"features\":[\"SAUNA\"]}]")));
    }

    @Test
    @DisplayName("Given malformed or empty JSON, when loaded, then a storage error is raised")
    void rejectsCorruptJson() {
        assertThrows(StorageException.class, () -> new JsonRoomRepository(json("[{\"id\":")));
        assertThrows(StorageException.class, () -> new JsonRoomRepository(json("")));
    }

    @Test
    @DisplayName("Given a missing resource, when loaded, then a storage error is raised")
    void rejectsMissingResource() {
        assertThrows(StorageException.class, () -> JsonRoomRepository.fromResource("/seed/missing.json"));
    }
}
