/*
 * Event.java
 *
 * Domain model for an event: its planning details, status, and one attendance
 * response per invitee.
 */
package com.campusplan.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An event being planned. Every invitee has exactly one response, listed in invitee order.
 * Only a {@link EventStatus#DRAFT} can have its details edited.
 *
 * <p>Instances are immutable; editing an event means creating a new instance with the same ID.
 * The room and start/end time of a scheduled event are added with scheduling in P2 (FR-05).
 *
 * @param id        generated identifier, never blank
 * @param details   the planning fields
 * @param status    where the event is in its life cycle
 * @param responses each invitee's reply, keyed by student ID
 */
public record Event(String id, EventDetails details, EventStatus status,
                    Map<String, AttendanceResponse> responses) {

    /**
     * Validates every field and checks that responses match the invitees exactly.
     *
     * @throws ValidationException if a field is missing or the responses do not match the invitees
     */
    public Event {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Event ID is required.");
        }
        if (details == null || status == null || responses == null) {
            throw new ValidationException("Event details, status, and responses are required.");
        }
        if (!responses.keySet().equals(Set.copyOf(details.inviteeIds()))
                // Not containsValue(null): immutable maps such as Map.of throw on a null argument.
                || responses.values().stream().anyMatch(Objects::isNull)) {
            throw new ValidationException("Event " + id + " must have exactly one response per invitee.");
        }
        // Copy in invitee order so listings follow the order the organizer entered.
        Map<String, AttendanceResponse> ordered = new LinkedHashMap<>();
        for (String invitee : details.inviteeIds()) {
            ordered.put(invitee, responses.get(invitee));
        }
        responses = Collections.unmodifiableMap(ordered);
    }

    /**
     * Creates a new draft where every invitee's response is PENDING.
     *
     * @param id      the event ID
     * @param details the planning fields
     * @return the draft event
     * @throws ValidationException if the ID is blank or the details are missing
     */
    public static Event draft(String id, EventDetails details) {
        if (details == null) {
            throw new ValidationException("Event details are required.");
        }
        return new Event(id, details, EventStatus.DRAFT, pending(details));
    }

    /**
     * Returns a copy of this draft with new planning fields. Invitees who stay keep their
     * response, new invitees start PENDING, and removed invitees lose theirs.
     *
     * @param newDetails the replacement planning fields
     * @return the edited draft, with the same ID
     * @throws ValidationException if this event is not a draft or the details are missing
     */
    public Event withDetails(EventDetails newDetails) {
        if (status != EventStatus.DRAFT) {
            throw new ValidationException("Only DRAFT events can be edited; event " + id + " is " + status + ".");
        }
        if (newDetails == null) {
            throw new ValidationException("Event details are required.");
        }
        Map<String, AttendanceResponse> kept = pending(newDetails);
        kept.replaceAll((invitee, pending) -> responses.getOrDefault(invitee, pending));
        return new Event(id, newDetails, status, kept);
    }

    /**
     * Builds a PENDING response for every invitee.
     *
     * @param details the planning fields
     * @return responses keyed by invitee ID
     */
    private static Map<String, AttendanceResponse> pending(EventDetails details) {
        Map<String, AttendanceResponse> responses = new LinkedHashMap<>();
        details.inviteeIds().forEach(invitee -> responses.put(invitee, AttendanceResponse.PENDING));
        return responses;
    }
}
