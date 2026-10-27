/*
 * EventRepository.java
 *
 * Persistence-layer abstraction for storing and retrieving events, so the
 * application layer does not depend on how events are stored.
 */
package com.campusplan.persistence;

import com.campusplan.domain.Event;

import java.util.List;
import java.util.Optional;

/** Storage for events. Implementations hold already-validated {@link Event} instances. */
public interface EventRepository {

    /**
     * Returns every stored event.
     *
     * @return an unmodifiable list of all events, in storage order
     */
    List<Event> findAll();

    /**
     * Looks up an event by ID.
     *
     * @param id the exact event ID
     * @return the event, or empty if no event has that ID
     */
    Optional<Event> findById(String id);

    /**
     * Inserts the event, or replaces the stored event with the same ID.
     *
     * @param event the event to store
     */
    void save(Event event);
}
