/*
 * AvailabilityInterval.java
 *
 * Domain model for one block of time on one date, with the validation rules
 * for 30-minute boundaries and opening hours, and the half-open overlap rule.
 */
package com.campusplan.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A half-open time block {@code [start, end)} on a single date. Times are local,
 * with no time-zone conversion. An interval ending at 14:00 does not overlap one starting at 14:00.
 *
 * @param date  the calendar date
 * @param start first minute of the block, on a 30-minute boundary, no earlier than {@link #OPENS}
 * @param end   minute just after the block, on a 30-minute boundary, no later than {@link #CLOSES}
 */
public record AvailabilityInterval(LocalDate date, LocalTime start, LocalTime end) implements Comparable<AvailabilityInterval> {

    /** Earliest start time; rooms open daily at 08:00. */
    public static final LocalTime OPENS = LocalTime.of(8, 0);

    /** Latest end time; rooms close daily at 22:00. */
    public static final LocalTime CLOSES = LocalTime.of(22, 0);

    /** Every start and end must fall on a multiple of this many minutes. */
    public static final int SLOT_MINUTES = 30;

    /**
     * Validates every field.
     *
     * @throws ValidationException if a field is missing, a time is off a 30-minute boundary,
     *                             the block falls outside opening hours, or start does not precede end
     */
    public AvailabilityInterval {
        if (date == null || start == null || end == null) {
            throw new ValidationException("Date, start time, and end time are required.");
        }
        if (!onSlotBoundary(start) || !onSlotBoundary(end)) {
            throw new ValidationException("Times must be on a " + SLOT_MINUTES + "-minute boundary, such as 09:00 or 09:30.");
        }
        if (!start.isBefore(end)) {
            throw new ValidationException("Start time must be before end time.");
        }
        if (start.isBefore(OPENS) || end.isAfter(CLOSES)) {
            throw new ValidationException("Times must be within opening hours " + OPENS + "-" + CLOSES + ".");
        }
    }

    /**
     * Whether this block shares at least one minute with another. Adjacent blocks do not overlap.
     *
     * @param other the block to compare with
     * @return true if both are on the same date and their half-open ranges intersect
     */
    public boolean overlaps(AvailabilityInterval other) {
        return date.equals(other.date) && start.isBefore(other.end) && other.start.isBefore(end);
    }

    /**
     * Whether this block overlaps or is directly adjacent to another, so the two can be merged.
     *
     * @param other the block to compare with
     * @return true if both are on the same date and neither ends before the other starts
     */
    public boolean overlapsOrTouches(AvailabilityInterval other) {
        return date.equals(other.date) && !start.isAfter(other.end) && !other.start.isAfter(end);
    }

    /**
     * Whether this block contains the whole of the given time range.
     *
     * @param onDate    date of the range
     * @param fromStart start of the range
     * @param toEnd     end of the range
     * @return true if the range lies on this date and entirely within {@code [start, end)}
     */
    public boolean covers(LocalDate onDate, LocalTime fromStart, LocalTime toEnd) {
        return date.equals(onDate) && !fromStart.isBefore(start) && !toEnd.isAfter(end);
    }

    /** Orders by date, then start time, then end time. */
    @Override
    public int compareTo(AvailabilityInterval other) {
        int byDate = date.compareTo(other.date);
        if (byDate != 0) {
            return byDate;
        }
        int byStart = start.compareTo(other.start);
        return byStart != 0 ? byStart : end.compareTo(other.end);
    }

    /**
     * Checks that a time is a whole number of slots past midnight.
     *
     * @param time the time to check
     * @return true if it has no seconds and its minutes are a multiple of {@link #SLOT_MINUTES}
     */
    private static boolean onSlotBoundary(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0 && time.getMinute() % SLOT_MINUTES == 0;
    }
}
