package com.campusplan.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** A bookable room. The name is stored trimmed; a null feature set means no features. */
public record Room(String id, String name, int capacity, Set<RoomFeature> features) {
    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_CAPACITY = 100;

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
        features = features == null || features.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(RoomFeature.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(features));
    }

    public boolean hasFeatures(Set<RoomFeature> required) {
        return features.containsAll(required);
    }
}
