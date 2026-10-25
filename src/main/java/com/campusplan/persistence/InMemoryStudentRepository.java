/*
 * InMemoryStudentRepository.java
 *
 * Persistence-layer implementation of StudentRepository that keeps students in memory
 * for the lifetime of the application.
 */
package com.campusplan.persistence;

import com.campusplan.domain.Student;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Student storage backed by a map; contents are lost when the application exits. */
public class InMemoryStudentRepository implements StudentRepository {

    /** Students keyed by ID; insertion order is kept so listings are stable. */
    private final Map<String, Student> students = new LinkedHashMap<>();

    /** {@inheritDoc} */
    @Override
    public List<Student> findAll() {
        return List.copyOf(students.values());
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Student> findById(String id) {
        return Optional.ofNullable(students.get(id));
    }

    /** {@inheritDoc} */
    @Override
    public void save(Student student) {
        students.put(student.id(), student);
    }
}
