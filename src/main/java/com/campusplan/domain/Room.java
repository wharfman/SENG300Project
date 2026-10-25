/*
 * Room.java
 *
 * Domain model for a room in the campus room catalogue, including the
 * validation rules for room names and capacities.
 */
package com.campusplan.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * A bookable room. The name is stored trimmed; a null feature set means no features.
 *
 * <p>Instances are immutable, including the feature set.
 *
 * @param id       catalogue identifier, never blank
 * @param name     display name, 1-60 characters after trimming
 * @param capacity number of seats, 1-100
 * @param features features the room provides, possibly empty
 */
public record Room(String id, String name, int capacity, Set<RoomFeature> features) {

    /** Maximum length of a room name, measured after trimming. */
    public static final int MAX_NAME_LENGTH = 60;

    /** Largest allowed room capacity. */
    public static final int MAX_CAPACITY = 100;

    /**
     * Validates and normalizes every field.
     *
     * @throws ValidationException if the ID is blank, the name is invalid, or the capacity is out of range
     */
    public Room {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Room ID is required.");
        }
        name = name == null ? "" : name.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(
                    "Room name must be 1-" + MAX_NAME_LENGTH + " characters after trimming.");
        }
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new ValidationException("Room capacity must be 1-" + MAX_CAPACITY + ".");
        }
        // EnumSet keeps features in declaration order for display; EnumSet.copyOf cannot copy an empty
        // non-EnumSet collection, hence the separate empty case.
        features = features == null || features.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(RoomFeature.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(features));
    }

    /**
     * Checks whether this room provides every required feature.
     *
     * @param required features that must all be present; an empty set always matches
     * @return true if the room has all of {@code required}
     */
    public boolean hasFeatures(Set<RoomFeature> required) {
        return features.containsAll(required);
    }
}
