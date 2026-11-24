/*
 * SchedulingService.java
 *
 * Application-layer service that generates and ranks scheduling suggestions for a
 * draft event, applying the room and invitee rules to every candidate, and confirms
 * a chosen suggestion after checking it again.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.Availability;
import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.EventDetails;
import com.campusplan.domain.EventStatus;
import com.campusplan.domain.Room;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.AvailabilityRepository;
import com.campusplan.persistence.EventRepository;
import com.campusplan.persistence.RoomRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Finds the best rooms and times for a draft event (FR-04) and schedules the one the
 * organizer picks (FR-05). The search only reads storage and reserves nothing; confirming
 * checks the choice against the same rules and the current data before saving it.
 *
 * <p>A candidate is one room and one start on a 30-minute boundary, within the date window
 * and opening hours. Room rules are applied in order and the first failure rejects the
 * candidate for its room. Otherwise each invitee is checked against every invitee rule, and
 * the candidate is rejected for attendance if fewer than the minimum are eligible.
 */
public class SchedulingService {

    /** Most suggestions returned. */
    public static final int MAX_SUGGESTIONS = 5;

    /** Room rules, applied in order; the first failure is the candidate's rejection. */
    private static final List<RoomRule> ROOM_RULES =
            List.of(new CapacityRule(), new FeatureRule(), new RoomFreeRule());

    /** Invitee rules; an invitee is eligible only if every rule passes. */
    private static final List<InviteeRule> INVITEE_RULES =
            List.of(new AvailabilityRule(), new NoConflictRule());

    /** Where events are stored. */
    private final EventRepository events;

    /** The room catalogue. */
    private final RoomRepository rooms;

    /** Where availability is stored. */
    private final AvailabilityRepository availability;

    /**
     * Creates a service over the given storage.
     *
     * @param events       event storage
     * @param rooms        the room catalogue
     * @param availability availability storage
     */
    public SchedulingService(EventRepository events, RoomRepository rooms, AvailabilityRepository availability) {
        this.events = events;
        this.rooms = rooms;
        this.availability = availability;
    }

    /**
     * Generates the top suggestions for a draft event, ranked by eligible count (highest
     * first), then date and time, then unused seats (fewest first), then room ID.
     *
     * @param eventId ID of the event, ignoring surrounding whitespace
     * @return up to {@link #MAX_SUGGESTIONS} suggestions and the rejection counts
     * @throws ValidationException if no event has the ID or the event is not a draft
     */
    public SuggestionResult suggest(String eventId) {
        Event event = find(eventId);
        if (event.status() != EventStatus.DRAFT) {
            throw new ValidationException("Only DRAFT events can get suggestions; event "
                    + event.id() + " is " + event.status() + ".");
        }

        Schedule schedule = snapshot();
        Map<String, Room> roomsById = new LinkedHashMap<>();
        rooms.findAll().forEach(room -> roomsById.put(room.id(), room));
        int invitees = event.details().inviteeIds().size();

        List<Suggestion> valid = new ArrayList<>();
        int rejectedForRoom = 0;
        int rejectedForAttendance = 0;
        for (AvailabilityInterval slot : slots(event.details())) {
            for (Room room : roomsById.values()) {
                if (roomFailure(event, room, slot, schedule).isPresent()) {
                    rejectedForRoom++;
                    continue;
                }
                Suggestion candidate = explain(event, room, slot, schedule);
                if (candidate.eligibleCount() < event.details().minimumAttendance()) {
                    rejectedForAttendance++;
                } else {
                    valid.add(candidate);
                }
            }
        }

        Comparator<Suggestion> ranking = Comparator.comparingInt(Suggestion::eligibleCount).reversed()
                .thenComparing(Suggestion::slot)
                .thenComparingInt(s -> roomsById.get(s.roomId()).capacity() - invitees)
                .thenComparing(Suggestion::roomId);
        List<Suggestion> top = valid.stream().sorted(ranking).limit(MAX_SUGGESTIONS).toList();
        return new SuggestionResult(top, rejectedForRoom, rejectedForAttendance);
    }

    /**
     * Schedules a draft event in a suggested room and time, reserving the room. Showing a
     * suggestion reserved nothing, so every rule is checked again against the current data
     * first. Responses are not changed; eligible invitees are not accepted automatically.
     *
     * @param eventId ID of the event, ignoring surrounding whitespace
     * @param chosen  a suggestion previously returned by {@link #suggest}; only its room and time are used
     * @return the stored, scheduled event
     * @throws ValidationException if the event is unknown or not a draft, the room is unknown, the time
     *                             does not fit the event, or the suggestion is no longer valid; nothing is changed
     */
    public Event confirm(String eventId, Suggestion chosen) {
        Event event = find(eventId);
        AvailabilityInterval slot = chosen.slot();
        // Checks the status first, so a cancelled event is reported as such rather than as a bad room.
        Event scheduled = event.schedule(chosen.roomId(), slot);
        Room room = rooms.findAll().stream()
                .filter(r -> r.id().equals(chosen.roomId()))
                .findFirst()
                .orElseThrow(() -> new ValidationException("No room with ID " + chosen.roomId() + "."));
        if (!slots(event.details()).contains(slot)) {
            throw new ValidationException(slot.date() + " " + slot.start() + "-" + slot.end()
                    + " is not a possible time for event " + event.id() + ".");
        }

        Schedule schedule = snapshot();
        Optional<String> roomProblem = roomFailure(event, room, slot, schedule);
        if (roomProblem.isPresent()) {
            throw new ValidationException("That suggestion is no longer valid: " + roomProblem.get() + ".");
        }
        int eligible = explain(event, room, slot, schedule).eligibleCount();
        int minimum = event.details().minimumAttendance();
        if (eligible < minimum) {
            throw new ValidationException("That suggestion is no longer valid: only " + eligible + " of "
                    + event.details().inviteeIds().size() + " invitees can attend, fewer than the minimum of "
                    + minimum + ".");
        }
        events.save(scheduled);
        return scheduled;
    }

    /**
     * Looks up an event, ignoring surrounding whitespace in the ID.
     *
     * @param eventId the event ID as entered
     * @return the event
     * @throws ValidationException if no event has the ID
     */
    private Event find(String eventId) {
        String trimmed = eventId == null ? "" : eventId.trim();
        return events.findById(trimmed)
                .orElseThrow(() -> new ValidationException("No event with ID " + trimmed + "."));
    }

    /**
     * Takes a read-only snapshot of the current events and availability for the rules to check.
     *
     * @return the snapshot
     */
    private Schedule snapshot() {
        return new Schedule(events.findAll(), availability.findAll().stream()
                .collect(Collectors.toMap(Availability::studentId, Function.identity())));
    }

    /**
     * Every candidate time: each 30-minute start in the date window whose end is within opening hours.
     *
     * @param details the event's planning fields
     * @return the slots, in date and time order
     */
    private static List<AvailabilityInterval> slots(EventDetails details) {
        List<AvailabilityInterval> slots = new ArrayList<>();
        LocalTime lastStart = AvailabilityInterval.CLOSES.minusMinutes(details.durationMinutes());
        for (LocalDate day = details.firstDay(); !day.isAfter(details.lastDay()); day = day.plusDays(1)) {
            for (LocalTime start = AvailabilityInterval.OPENS; !start.isAfter(lastStart);
                    start = start.plusMinutes(AvailabilityInterval.SLOT_MINUTES)) {
                slots.add(new AvailabilityInterval(day, start, start.plusMinutes(details.durationMinutes())));
            }
        }
        return slots;
    }

    /**
     * The first room rule that rejects the room at this time, in rule order.
     *
     * @param event    the event being planned
     * @param room     the candidate room
     * @param slot     the candidate time
     * @param schedule what is already planned
     * @return that rule's reason, or empty if every room rule passes
     */
    private static Optional<String> roomFailure(Event event, Room room, AvailabilityInterval slot, Schedule schedule) {
        return ROOM_RULES.stream()
                .map(rule -> rule.check(event, room, slot, schedule))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Sorts the invitees into eligible and ineligible for a candidate. An ineligible invitee's
     * reason lists every rule they fail, separated by semicolons.
     *
     * @param event    the event being planned
     * @param room     the candidate room, which passed every room rule
     * @param slot     the candidate time
     * @param schedule what is already planned
     * @return the candidate with its eligible invitees and reasons
     */
    private static Suggestion explain(Event event, Room room, AvailabilityInterval slot, Schedule schedule) {
        List<String> eligible = new ArrayList<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        for (String invitee : event.details().inviteeIds()) {
            List<String> failures = INVITEE_RULES.stream()
                    .map(rule -> rule.check(invitee, slot, schedule))
                    .flatMap(Optional::stream)
                    .toList();
            if (failures.isEmpty()) {
                eligible.add(invitee);
            } else {
                reasons.put(invitee, String.join("; ", failures));
            }
        }
        return new Suggestion(room.id(), slot, eligible, reasons);
    }
}
