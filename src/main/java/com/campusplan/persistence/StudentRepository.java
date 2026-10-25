package com.campusplan.persistence;

import com.campusplan.domain.Student;

import java.util.List;
import java.util.Optional;

public interface StudentRepository {
    List<Student> findAll();

    Optional<Student> findById(String id);

    /** Inserts the student, or replaces the stored student with the same ID. */
    void save(Student student);
}
