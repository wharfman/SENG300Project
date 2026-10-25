package com.campusplan.persistence;

import com.campusplan.domain.Room;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Room catalogue read once from a UTF-8 JSON array of rooms. */
public class JsonRoomRepository implements RoomRepository {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<Room> rooms;

    public JsonRoomRepository(InputStream json) {
        this.rooms = read(json);
        checkUnique(rooms);
    }

    /** Loads the catalogue from a classpath resource such as {@code /seed/rooms.json}. */
    public static JsonRoomRepository fromResource(String resourcePath) {
        try (InputStream in = JsonRoomRepository.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new StorageException("Room catalogue not found: " + resourcePath);
            }
            return new JsonRoomRepository(in);
        } catch (IOException e) {
            throw new StorageException("Could not read room catalogue: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Room> findAll() {
        return rooms;
    }

    private static List<Room> read(InputStream json) {
        List<Room> rooms;
        try {
            rooms = MAPPER.readValue(json, new TypeReference<List<Room>>() { });
        } catch (JsonProcessingException e) {
            throw new StorageException("Room catalogue is invalid: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new StorageException("Could not read room catalogue: " + e.getMessage(), e);
        }
        if (rooms == null) {
            throw new StorageException("Room catalogue is invalid: expected a JSON array of rooms.");
        }
        return List.copyOf(rooms);
    }

    private static void checkUnique(List<Room> rooms) {
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Room room : rooms) {
            if (!ids.add(room.id())) {
                throw new StorageException("Room catalogue is invalid: duplicate room ID " + room.id() + ".");
            }
            if (!names.add(room.name().toLowerCase(Locale.ROOT))) {
                throw new StorageException("Room catalogue is invalid: duplicate room name " + room.name() + ".");
            }
        }
    }
}
