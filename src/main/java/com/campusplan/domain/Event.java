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
 * A {@link EventStatus#SCHEDULED} event has a room and a time; no other status has either.
 *
 * @param id        generated identifier, never blank
 * @param details   the planning fields
 * @param status    where the event is in its life cycle
 * @param responses each invitee's reply, keyed by student ID
 * @param roomId    reserved room, set only when SCHEDULED, otherwise null
 * @param slot      reserved date and time, set only when SCHEDULED, otherwise null
 */
public record Event(String id, EventDetails details, EventStatus status,
                    Map<String, AttendanceResponse> responses, String roomId, AvailabilityInterval slot) {

    /**
     * Creates an event with no room or time, for any status other than SCHEDULED.
     *
     * @param id        generated identifier, never blank
     * @param details   the planning fields
     * @param status    where the event is in its life cycle
     * @param responses each invitee's reply, keyed by student ID
     * @throws ValidationException if a field is missing, the responses do not match the invitees,
     *                             or the status is SCHEDULED
     */
    public Event(String id, EventDetails details, EventStatus status, Map<String, AttendanceResponse> responses) {
        this(id, details, status, responses, null, null);
    }

    /**
     * Validates every field and checks that responses match the invitees exactly.
     *
     * @throws ValidationException if a field is missing, the responses do not match the invitees,
     *                             or the room and time do not match the status
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
        if (status == EventStatus.SCHEDULED && (roomId == null || roomId.isBlank() || slot == null)) {
            throw new ValidationException("Scheduled event " + id + " needs a room and a time.");
        }
        if (status != EventStatus.SCHEDULED && (roomId != null || slot != null)) {
            throw new ValidationException("Only SCHEDULED events have a room and time; event " + id + " is " + status + ".");
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
     * Returns a copy of this draft as SCHEDULED in the given room and time. Responses are kept
     * as they are; nobody is accepted automatically.
     *
     * @param room    ID of the room to reserve
     * @param time    the date and time to reserve
     * @return the scheduled event, with the same ID
     * @throws ValidationException if this event is not a draft, or the room or time is missing
     */
    public Event schedule(String room, AvailabilityInterval time) {
        if (status == EventStatus.CANCELLED) {
            throw new ValidationException("Event " + id + " is CANCELLED and cannot be scheduled again.");
        }
        if (status != EventStatus.DRAFT) {
            throw new ValidationException("Only DRAFT events can be scheduled; event " + id + " is " + status + ".");
        }
        return new Event(id, details, EventStatus.SCHEDULED, responses, room, time);
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
