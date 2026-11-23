/*
 * SchedulingService.java
 *
 * Application-layer service that generates and ranks scheduling suggestions for a
 * draft event, applying the room and invitee rules to every candidate.
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
 * Finds the best rooms and times for a draft event (FR-04). The search only reads storage
 * and reserves nothing.
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
        String trimmed = eventId == null ? "" : eventId.trim();
        Event event = events.findById(trimmed)
                .orElseThrow(() -> new ValidationException("No event with ID " + trimmed + "."));
        if (event.status() != EventStatus.DRAFT) {
            throw new ValidationException("Only DRAFT events can get suggestions; event "
                    + event.id() + " is " + event.status() + ".");
        }

        Schedule schedule = new Schedule(events.findAll(), availability.findAll().stream()
                .collect(Collectors.toMap(Availability::studentId, Function.identity())));
        Map<String, Room> roomsById = new LinkedHashMap<>();
        rooms.findAll().forEach(room -> roomsById.put(room.id(), room));
        int invitees = event.details().inviteeIds().size();

        List<Suggestion> valid = new ArrayList<>();
        int rejectedForRoom = 0;
        int rejectedForAttendance = 0;
        for (AvailabilityInterval slot : slots(event.details())) {
            for (Room room : roomsById.values()) {
                if (failsRoomRule(event, room, slot, schedule)) {
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
     * Whether any room rule rejects the room at this time.
     *
     * @param event    the event being planned
     * @param room     the candidate room
     * @param slot     the candidate time
     * @param schedule what is already planned
     * @return true if a room rule fails
     */
    private static boolean failsRoomRule(Event event, Room room, AvailabilityInterval slot, Schedule schedule) {
        return ROOM_RULES.stream().anyMatch(rule -> rule.check(event, room, slot, schedule).isPresent());
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
