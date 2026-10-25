package com.campusplan.persistence;

import com.campusplan.domain.Student;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryStudentRepository implements StudentRepository {
    private final Map<String, Student> students = new LinkedHashMap<>();

    @Override
    public List<Student> findAll() {
        return List.copyOf(students.values());
    }

    @Override
    public Optional<Student> findById(String id) {
        return Optional.ofNullable(students.get(id));
    }

    @Override
    public void save(Student student) {
        students.put(student.id(), student);
    }
}
