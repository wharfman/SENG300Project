/*
 * InMemoryAvailabilityRepository.java
 *
 * Persistence-layer implementation of AvailabilityRepository that keeps availability
 * in memory for the lifetime of the application.
 */
package com.campusplan.persistence;

import com.campusplan.domain.Availability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Availability storage backed by a map; contents are lost when the application exits. */
public class InMemoryAvailabilityRepository implements AvailabilityRepository {

    /** Availability keyed by student ID; insertion order is kept so listings are stable. */
    private final Map<String, Availability> availability = new LinkedHashMap<>();

    /** {@inheritDoc} */
    @Override
    public List<Availability> findAll() {
        return List.copyOf(availability.values());
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Availability> findByStudentId(String studentId) {
        return Optional.ofNullable(availability.get(studentId));
    }

    /** {@inheritDoc} */
    @Override
    public void save(Availability entry) {
        availability.put(entry.studentId(), entry);
    }
}
