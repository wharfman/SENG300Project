/*
 * AvailabilityService.java
 *
 * Application-layer service for the availability workflows: add, remove, and view
 * a student's free time, and check whether a student is free for an event.
 */
package com.campusplan.application;

import com.campusplan.domain.Availability;
import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.AvailabilityRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Availability operations used by the user interface. Interval rules are checked by
 * {@link AvailabilityInterval} and merging by {@link Availability}; this class adds the check
 * that the student exists. A rejected operation leaves storage unchanged.
 *
 * <p>Rejecting edits that would invalidate ACCEPTED attendance is planned for P2 (FR-06),
 * when attendance responses are recorded.
 */
public class AvailabilityService {

    /** Where availability is stored. */
    private final AvailabilityRepository repository;

    /** Used to check that a student exists before their availability is touched. */
    private final StudentService students;

    /**
     * Creates a service over the given storage.
     *
     * @param repository availability storage
     * @param students   student lookup
     */
    public AvailabilityService(AvailabilityRepository repository, StudentService students) {
        this.repository = repository;
        this.students = students;
    }

    /**
     * Adds free time for a student, merging it with any overlapping or adjacent interval.
     *
     * @param studentId the student ID as entered
     * @param date      date of the free time
     * @param start     start time
     * @param end       end time
     * @return the student's stored availability after the change
     * @throws ValidationException if no student has the ID or the interval is invalid
     */
    public List<AvailabilityInterval> add(String studentId, LocalDate date, LocalTime start, LocalTime end) {
        Availability current = current(studentId);
        Availability updated = current.with(new AvailabilityInterval(date, start, end));
        repository.save(updated);
        return updated.intervals();
    }

    /**
     * Removes free time for a student, trimming or splitting any interval it overlaps.
     *
     * @param studentId the student ID as entered
     * @param date      date of the time to remove
     * @param start     start time
     * @param end       end time
     * @return the student's stored availability after the change
     * @throws ValidationException if no student has the ID, the interval is invalid,
     *                             or the student has no availability overlapping it
     */
    public List<AvailabilityInterval> remove(String studentId, LocalDate date, LocalTime start, LocalTime end) {
        Availability current = current(studentId);
        Availability updated = current.without(new AvailabilityInterval(date, start, end));
        repository.save(updated);
        return updated.intervals();
    }

    /**
     * Returns a student's free time.
     *
     * @param studentId the student ID as entered
     * @return the merged intervals, sorted by date and start; empty if none were entered
     * @throws ValidationException if no student has the ID
     */
    public List<AvailabilityInterval> get(String studentId) {
        return current(studentId).intervals();
    }

    /**
     * Whether a student is free for the whole of an event's time.
     *
     * @param studentId the student ID as entered
     * @param date      date of the event
     * @param start     start of the event
     * @param end       end of the event
     * @return true if one interval covers the complete range
     * @throws ValidationException if no student has the ID
     */
    public boolean isAvailable(String studentId, LocalDate date, LocalTime start, LocalTime end) {
        return current(studentId).isAvailable(date, start, end);
    }

    /**
     * Looks up the stored availability of an existing student.
     *
     * @param studentId the student ID as entered
     * @return the stored availability, or empty availability if none was stored
     * @throws ValidationException if no student has the ID
     */
    private Availability current(String studentId) {
        String id = students.get(studentId).id();
        return repository.findByStudentId(id).orElseGet(() -> Availability.empty(id));
    }
}
