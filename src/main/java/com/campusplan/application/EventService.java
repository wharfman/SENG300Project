/*
 * EventService.java
 *
 * Application-layer service for the draft event workflows: create, edit, view, and list.
 * Enforces rules that need other records, such as invitees being existing students.
 */
package com.campusplan.application;

import com.campusplan.domain.Event;
import com.campusplan.domain.EventDetails;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.EventRepository;

import java.util.List;

/**
 * Event operations used by the user interface. Field rules are checked by {@link EventDetails}
 * and draft rules by {@link Event}; this class adds ID generation and the check that every
 * invitee is an existing student. A rejected operation leaves storage unchanged.
 */
public class EventService {

    /** Where events are stored. */
    private final EventRepository repository;

    /** Used to check that every invitee exists. */
    private final StudentService students;

    /**
     * Creates a service over the given storage.
     *
     * @param repository event storage
     * @param students   student lookup
     */
    public EventService(EventRepository repository, StudentService students) {
        this.repository = repository;
        this.students = students;
    }

    /**
     * Creates and stores a new draft event with a generated ID and PENDING responses.
     *
     * @param details the planning fields
     * @return the stored draft
     * @throws ValidationException if the details are missing or an invitee is not an existing student
     */
    public Event create(EventDetails details) {
        ensureInviteesExist(details);
        Event event = Event.draft(nextId(), details);
        repository.save(event);
        return event;
    }

    /**
     * Replaces the planning fields of an existing draft. The ID never changes.
     *
     * @param id      ID of the event to edit
     * @param details the new planning fields
     * @return the stored, updated draft
     * @throws ValidationException if no event has the ID, it is not a draft,
     *                             or an invitee is not an existing student
     */
    public Event edit(String id, EventDetails details) {
        Event existing = get(id);
        ensureInviteesExist(details);
        Event updated = existing.withDetails(details);
        repository.save(updated);
        return updated;
    }

    /**
     * Looks up an event by ID, ignoring surrounding whitespace.
     *
     * @param id the event ID as entered
     * @return the matching event
     * @throws ValidationException if no event has the ID
     */
    public Event get(String id) {
        String trimmed = id == null ? "" : id.trim();
        return repository.findById(trimmed)
                .orElseThrow(() -> new ValidationException("No event with ID " + trimmed + "."));
    }

    /**
     * Returns every event.
     *
     * @return all events, in storage order
     */
    public List<Event> list() {
        return repository.findAll();
    }

    /**
     * Rejects details that invite a student who does not exist.
     *
     * @param details the planning fields to check
     * @throws ValidationException if the details are missing or an invitee ID is unknown
     */
    private void ensureInviteesExist(EventDetails details) {
        if (details == null) {
            throw new ValidationException("Event details are required.");
        }
        details.inviteeIds().forEach(students::get);
    }

    /**
     * IDs are E001, E002, ...; the next one follows the highest existing number.
     *
     * @return the next unused event ID
     */
    private String nextId() {
        int max = repository.findAll().stream()
                .map(Event::id)
                .filter(id -> id.matches("E\\d+"))
                .mapToInt(id -> Integer.parseInt(id.substring(1)))
                .max()
                .orElse(0);
        return String.format("E%03d", max + 1);
    }
}
