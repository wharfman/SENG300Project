/*
 * EventDetails.java
 *
 * Domain model for the planning fields of an event, with the validation rules
 * for title, invitees, date window, duration, and minimum attendance.
 */
package com.campusplan.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The planning fields of an event: what the organizer enters for a draft. The title and
 * invitee IDs are stored trimmed. Whether each invitee is a real student is checked by the
 * application layer, which can see the student list.
 *
 * <p>Instances are immutable, including the invitee list and feature set.
 *
 * @param title             event title, 1-100 characters after trimming
 * @param inviteeIds        2-20 distinct student IDs, in the order entered
 * @param firstDay          first day the event may take place
 * @param lastDay           last day the event may take place; the window is 1-7 days inclusive
 * @param durationMinutes   length of the event, 30-180 minutes in 30-minute steps
 * @param requiredFeatures  features the room must provide, possibly empty
 * @param minimumAttendance fewest eligible invitees needed, from 2 to the invitee count
 */
public record EventDetails(String title, List<String> inviteeIds, LocalDate firstDay, LocalDate lastDay,
                           int durationMinutes, Set<RoomFeature> requiredFeatures, int minimumAttendance) {

    /** Maximum length of an event title, measured after trimming. */
    public static final int MAX_TITLE_LENGTH = 100;

    /** Fewest students an event can invite. */
    public static final int MIN_INVITEES = 2;

    /** Most students an event can invite. */
    public static final int MAX_INVITEES = 20;

    /** Longest date window, in days. */
    public static final int MAX_WINDOW_DAYS = 7;

    /** Shortest event, in minutes. */
    public static final int MIN_DURATION = 30;

    /** Longest event, in minutes. */
    public static final int MAX_DURATION = 180;

    /**
     * Validates and normalizes every field.
     *
     * @throws ValidationException if any field breaks its rule
     */
    public EventDetails {
        title = title == null ? "" : title.trim();
        if (title.isEmpty() || title.length() > MAX_TITLE_LENGTH) {
            throw new ValidationException(
                    "Event title must be 1-" + MAX_TITLE_LENGTH + " characters after trimming.");
        }
        inviteeIds = validateInvitees(inviteeIds);
        validateWindow(firstDay, lastDay);
        if (durationMinutes < MIN_DURATION || durationMinutes > MAX_DURATION
                || durationMinutes % AvailabilityInterval.SLOT_MINUTES != 0) {
            throw new ValidationException("Duration must be " + MIN_DURATION + "-" + MAX_DURATION
                    + " minutes in " + AvailabilityInterval.SLOT_MINUTES + "-minute steps.");
        }
        if (minimumAttendance < MIN_INVITEES || minimumAttendance > inviteeIds.size()) {
            throw new ValidationException("Minimum attendance must be " + MIN_INVITEES
                    + "-" + inviteeIds.size() + " (the number of invitees).");
        }
        // Same empty-set handling as Room: EnumSet.copyOf cannot copy an empty non-EnumSet collection.
        requiredFeatures = requiredFeatures == null || requiredFeatures.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(RoomFeature.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(requiredFeatures));
    }

    /**
     * Number of days in the date window, counting both ends.
     *
     * @return 1-7
     */
    public int windowDays() {
        return (int) ChronoUnit.DAYS.between(firstDay, lastDay) + 1;
    }

    /**
     * Trims invitee IDs and checks the count and that none repeat.
     *
     * @param ids the IDs as entered, possibly null
     * @return an unmodifiable list of trimmed IDs in the order entered
     * @throws ValidationException if an ID is blank, an ID repeats, or the count is out of range
     */
    private static List<String> validateInvitees(List<String> ids) {
        List<String> trimmed = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : ids == null ? List.<String>of() : ids) {
            String clean = id == null ? "" : id.trim();
            if (clean.isEmpty()) {
                throw new ValidationException("Invitee IDs must not be blank.");
            }
            if (!seen.add(clean)) {
                throw new ValidationException("Student " + clean + " is invited more than once.");
            }
            trimmed.add(clean);
        }
        if (trimmed.size() < MIN_INVITEES || trimmed.size() > MAX_INVITEES) {
            throw new ValidationException(
                    "An event must invite " + MIN_INVITEES + "-" + MAX_INVITEES + " distinct students.");
        }
        return List.copyOf(trimmed);
    }

    /**
     * Checks that the date window is present, in order, and 1-7 days long.
     *
     * @param firstDay first day of the window
     * @param lastDay  last day of the window
     * @throws ValidationException if a day is missing, the last day is before the first, or the window is too long
     */
    private static void validateWindow(LocalDate firstDay, LocalDate lastDay) {
        if (firstDay == null || lastDay == null) {
            throw new ValidationException("First and last day of the date window are required.");
        }
        if (lastDay.isBefore(firstDay)) {
            throw new ValidationException("Last day must not be before the first day.");
        }
        if (ChronoUnit.DAYS.between(firstDay, lastDay) + 1 > MAX_WINDOW_DAYS) {
            throw new ValidationException("Date window must be 1-" + MAX_WINDOW_DAYS + " consecutive days.");
        }
    }
}
