/*
 * InMemoryEventRepository.java
 *
 * Persistence-layer implementation of EventRepository that keeps events in memory
 * for the lifetime of the application.
 */
package com.campusplan.persistence;

import com.campusplan.domain.Event;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Event storage backed by a map; contents are lost when the application exits. */
public class InMemoryEventRepository implements EventRepository {

    /** Events keyed by ID; insertion order is kept so listings are stable. */
    private final Map<String, Event> events = new LinkedHashMap<>();

    /** {@inheritDoc} */
    @Override
    public List<Event> findAll() {
        return List.copyOf(events.values());
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Event> findById(String id) {
        return Optional.ofNullable(events.get(id));
    }

    /** {@inheritDoc} */
    @Override
    public void save(Event event) {
        events.put(event.id(), event);
    }
}
