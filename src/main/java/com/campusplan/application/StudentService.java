package com.campusplan.application;

import com.campusplan.domain.Student;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.StudentRepository;

import java.util.List;
import java.util.Locale;

public class StudentService {
    private final StudentRepository repository;

    public StudentService(StudentRepository repository) {
        this.repository = repository;
    }

    public Student create(String name, String email) {
        Student student = new Student(nextId(), name, email);
        ensureEmailUnused(student.email(), student.id());
        repository.save(student);
        return student;
    }

    /** Replaces the name and email of an existing student. The ID never changes. */
    public Student edit(String id, String name, String email) {
        Student existing = get(id);
        Student updated = new Student(existing.id(), name, email);
        ensureEmailUnused(updated.email(), updated.id());
        repository.save(updated);
        return updated;
    }

    public Student get(String id) {
        String trimmed = id == null ? "" : id.trim();
        return repository.findById(trimmed)
                .orElseThrow(() -> new ValidationException("No student with ID " + trimmed + "."));
    }

    /** Case-insensitive partial match on name or email. */
    public List<Student> search(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return repository.findAll().stream()
                .filter(s -> s.name().toLowerCase(Locale.ROOT).contains(needle)
                        || s.email().contains(needle))
                .toList();
    }

    private void ensureEmailUnused(String email, String ownId) {
        boolean taken = repository.findAll().stream()
                .anyMatch(s -> s.email().equals(email) && !s.id().equals(ownId));
        if (taken) {
            throw new ValidationException("Email " + email + " is already used by another student.");
        }
    }

    /** IDs are S001, S002, ...; the next one follows the highest existing number. */
    private String nextId() {
        int max = repository.findAll().stream()
                .map(Student::id)
                .filter(id -> id.matches("S\\d+"))
                .mapToInt(id -> Integer.parseInt(id.substring(1)))
                .max()
                .orElse(0);
        return String.format("S%03d", max + 1);
    }
}
