/*
 * AvailabilityRepository.java
 *
 * Persistence-layer abstraction for storing and retrieving student availability,
 * so the application layer does not depend on how availability is stored.
 */
package com.campusplan.persistence;

import com.campusplan.domain.Availability;

import java.util.List;
import java.util.Optional;

/** Storage for availability, one {@link Availability} per student. */
public interface AvailabilityRepository {

    /**
     * Returns the availability of every student that has any stored.
     *
     * @return an unmodifiable list of all stored availability, in storage order
     */
    List<Availability> findAll();

    /**
     * Looks up a student's availability.
     *
     * @param studentId the exact student ID
     * @return the student's availability, or empty if none has been stored
     */
    Optional<Availability> findByStudentId(String studentId);

    /**
     * Inserts the availability, or replaces the stored availability for the same student.
     *
     * @param availability the availability to store
     */
    void save(Availability availability);
}
