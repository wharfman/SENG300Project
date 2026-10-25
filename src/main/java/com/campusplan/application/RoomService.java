package com.campusplan.application;

import com.campusplan.domain.Room;
import com.campusplan.domain.RoomFeature;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.RoomRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class RoomService {
    private final RoomRepository repository;

    public RoomService(RoomRepository repository) {
        this.repository = repository;
    }

    /** All rooms, ordered by ID. */
    public List<Room> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Room::id))
                .toList();
    }

    /** Rooms seating at least {@code minCapacity} that have every required feature. */
    public List<Room> filter(int minCapacity, Set<RoomFeature> requiredFeatures) {
        if (minCapacity < 1) {
            throw new ValidationException("Minimum capacity must be at least 1.");
        }
        return list().stream()
                .filter(r -> r.capacity() >= minCapacity && r.hasFeatures(requiredFeatures))
                .toList();
    }
}
