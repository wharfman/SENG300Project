/*
 * JsonRoomRepository.java
 *
 * Persistence-layer implementation of RoomRepository that loads the room catalogue
 * from JSON using Jackson and checks catalogue-wide rules.
 */
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

/**
 * Room catalogue read once from a UTF-8 JSON array of rooms.
 *
 * <p>Each room is validated by {@link Room} as it is read; this class additionally
 * rejects catalogues with duplicate IDs or names. Any problem is reported as a
 * {@link StorageException}.
 */
public class JsonRoomRepository implements RoomRepository {

    /** Shared Jackson mapper; thread-safe once configured. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The loaded catalogue. */
    private final List<Room> rooms;

    /**
     * Reads and validates a catalogue from a stream. The stream is not closed.
     *
     * @param json UTF-8 JSON array of room objects
     * @throws StorageException if the JSON is unreadable, malformed, or breaks a room rule
     */
    public JsonRoomRepository(InputStream json) {
        this.rooms = read(json);
        checkUnique(rooms);
    }

    /**
     * Loads the catalogue from a classpath resource such as {@code /seed/rooms.json}.
     *
     * @param resourcePath absolute classpath path of the JSON file
     * @return a repository holding the loaded catalogue
     * @throws StorageException if the resource is missing, unreadable, or invalid
     */
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

    /** {@inheritDoc} */
    @Override
    public List<Room> findAll() {
        return rooms;
    }

    /**
     * Parses the JSON into rooms.
     *
     * @param json UTF-8 JSON array of room objects
     * @return an unmodifiable list of the parsed rooms
     * @throws StorageException if the JSON cannot be read or parsed into valid rooms
     */
    private static List<Room> read(InputStream json) {
        List<Room> rooms;
        try {
            rooms = MAPPER.readValue(json, new TypeReference<List<Room>>() { });
        } catch (JsonProcessingException e) {
            // Also covers a ValidationException thrown by the Room constructor during parsing.
            throw new StorageException("Room catalogue is invalid: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new StorageException("Could not read room catalogue: " + e.getMessage(), e);
        }
        // Jackson returns null for a JSON document that is just "null".
        if (rooms == null) {
            throw new StorageException("Room catalogue is invalid: expected a JSON array of rooms.");
        }
        return List.copyOf(rooms);
    }

    /**
     * Ensures no two rooms share an ID, or a name ignoring case. Also used by {@link JsonDataStore}.
     *
     * @param rooms the parsed catalogue
     * @throws StorageException naming the first duplicate found
     */
    static void checkUnique(List<Room> rooms) {
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
