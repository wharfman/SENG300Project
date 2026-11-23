/*
 * FeatureRule.java
 *
 * Application-layer room rule: the room must provide every feature the event requires.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.Room;

import java.util.List;
import java.util.Optional;

/** Fails a room that lacks any required feature, naming every missing one. */
public class FeatureRule implements RoomRule {

    /** {@inheritDoc} */
    @Override
    public Optional<String> check(Event event, Room room, AvailabilityInterval slot, Schedule schedule) {
        List<String> missing = event.details().requiredFeatures().stream()
                .filter(feature -> !room.features().contains(feature))
                .map(Enum::name)
                .toList();
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("room lacks " + String.join(", ", missing));
    }
}
