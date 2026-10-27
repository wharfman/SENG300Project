/*
 * Availability.java
 *
 * Domain model for all the time one student is free, with the rule that
 * overlapping or adjacent intervals are merged.
 */
package com.campusplan.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The time a student is free. Intervals are always stored merged and sorted, so no two
 * overlap or touch. Missing availability means unavailable.
 *
 * <p>Instances are immutable; adding or removing time returns a new instance.
 *
 * @param studentId ID of the student this availability belongs to, never blank
 * @param intervals free time, merged and sorted by date and start
 */
public record Availability(String studentId, List<AvailabilityInterval> intervals) {

    /**
     * Validates the student ID and merges the intervals.
     *
     * @throws ValidationException if the student ID is blank
     */
    public Availability {
        if (studentId == null || studentId.isBlank()) {
            throw new ValidationException("Student ID is required.");
        }
        intervals = merge(intervals == null ? List.of() : intervals);
    }

    /**
     * Creates availability with no free time.
     *
     * @param studentId ID of the student
     * @return availability with no intervals
     */
    public static Availability empty(String studentId) {
        return new Availability(studentId, List.of());
    }

    /**
     * Returns a copy with the given time added, merged with any overlapping or adjacent interval.
     *
     * @param interval the free time to add
     * @return the updated availability
     */
    public Availability with(AvailabilityInterval interval) {
        List<AvailabilityInterval> added = new ArrayList<>(intervals);
        added.add(interval);
        return new Availability(studentId, added);
    }

    /**
     * Returns a copy with the given time removed. Intervals partly inside it are trimmed or split.
     *
     * @param removed the time that is no longer free
     * @return the updated availability
     * @throws ValidationException if no stored interval overlaps the removed time
     */
    public Availability without(AvailabilityInterval removed) {
        if (intervals.stream().noneMatch(removed::overlaps)) {
            throw new ValidationException("Student " + studentId + " has no availability on "
                    + removed.date() + " between " + removed.start() + " and " + removed.end() + ".");
        }
        List<AvailabilityInterval> remaining = new ArrayList<>();
        for (AvailabilityInterval interval : intervals) {
            if (!interval.overlaps(removed)) {
                remaining.add(interval);
                continue;
            }
            // Keep whatever lies before and after the removed block.
            if (interval.start().isBefore(removed.start())) {
                remaining.add(new AvailabilityInterval(interval.date(), interval.start(), removed.start()));
            }
            if (removed.end().isBefore(interval.end())) {
                remaining.add(new AvailabilityInterval(interval.date(), removed.end(), interval.end()));
            }
        }
        return new Availability(studentId, remaining);
    }

    /**
     * Whether the student is free for the whole of the given time range.
     *
     * @param date  date of the event
     * @param start start of the event
     * @param end   end of the event
     * @return true if a single interval covers the complete range
     */
    public boolean isAvailable(LocalDate date, LocalTime start, LocalTime end) {
        // Intervals are merged, so a range across a join is covered by the one merged interval.
        return intervals.stream().anyMatch(i -> i.covers(date, start, end));
    }

    /**
     * Sorts intervals and combines any that overlap or touch.
     *
     * @param unsorted intervals in any order, possibly overlapping
     * @return an unmodifiable sorted list where no two intervals overlap or touch
     */
    private static List<AvailabilityInterval> merge(List<AvailabilityInterval> unsorted) {
        List<AvailabilityInterval> sorted = unsorted.stream().sorted().toList();
        List<AvailabilityInterval> merged = new ArrayList<>();
        for (AvailabilityInterval next : sorted) {
            int last = merged.size() - 1;
            if (last >= 0 && merged.get(last).overlapsOrTouches(next)) {
                AvailabilityInterval current = merged.get(last);
                LocalTime end = current.end().isAfter(next.end()) ? current.end() : next.end();
                merged.set(last, new AvailabilityInterval(current.date(), current.start(), end));
            } else {
                merged.add(next);
            }
        }
        return List.copyOf(merged);
    }
}
