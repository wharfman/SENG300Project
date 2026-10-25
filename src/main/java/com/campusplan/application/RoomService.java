/*
 * RoomService.java
 *
 * Application-layer service for browsing the room catalogue: listing rooms and
 * filtering them by capacity and required features.
 */
package com.campusplan.application;

import com.campusplan.domain.Room;
import com.campusplan.domain.RoomFeature;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.RoomRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Room catalogue queries used by the user interface. Results are always ordered by room ID. */
public class RoomService {

    /** Source of the room catalogue. */
    private final RoomRepository repository;

    /**
     * Creates a service over the given catalogue.
     *
     * @param repository room catalogue source
     */
    public RoomService(RoomRepository repository) {
        this.repository = repository;
    }

    /**
     * All rooms, ordered by ID.
     *
     * @return every room in the catalogue
     */
    public List<Room> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Room::id))
                .toList();
    }

    /**
     * Rooms seating at least {@code minCapacity} that have every required feature.
     *
     * @param minCapacity      smallest acceptable capacity, at least 1
     * @param requiredFeatures features each room must have; empty means no requirement
     * @return matching rooms ordered by ID, possibly empty
     * @throws ValidationException if {@code minCapacity} is below 1
     */
    public List<Room> filter(int minCapacity, Set<RoomFeature> requiredFeatures) {
        if (minCapacity < 1) {
            throw new ValidationException("Minimum capacity must be at least 1.");
        }
        return list().stream()
                .filter(r -> r.capacity() >= minCapacity && r.hasFeatures(requiredFeatures))
                .toList();
    }
}
