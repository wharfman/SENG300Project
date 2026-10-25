/*
 * StudentRepository.java
 *
 * Persistence-layer abstraction for storing and retrieving students, so the
 * application layer does not depend on how students are stored.
 */
package com.campusplan.persistence;

import com.campusplan.domain.Student;

import java.util.List;
import java.util.Optional;

/** Storage for students. Implementations hold already-validated {@link Student} instances. */
public interface StudentRepository {

    /**
     * Returns every stored student.
     *
     * @return an unmodifiable list of all students, in storage order
     */
    List<Student> findAll();

    /**
     * Looks up a student by ID.
     *
     * @param id the exact student ID
     * @return the student, or empty if no student has that ID
     */
    Optional<Student> findById(String id);

    /**
     * Inserts the student, or replaces the stored student with the same ID.
     *
     * @param student the student to store
     */
    void save(Student student);
}
