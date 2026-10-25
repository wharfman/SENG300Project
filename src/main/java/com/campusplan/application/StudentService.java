/*
 * StudentService.java
 *
 * Application-layer service for the student workflows: create, edit, view, and search.
 * Enforces rules that span more than one student, such as unique emails.
 */
package com.campusplan.application;

import com.campusplan.domain.Student;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.StudentRepository;

import java.util.List;
import java.util.Locale;

/**
 * Student operations used by the user interface. Field rules are checked by {@link Student};
 * this class adds ID generation and email uniqueness. A rejected operation leaves storage unchanged.
 */
public class StudentService {

    /** Where students are stored. */
    private final StudentRepository repository;

    /**
     * Creates a service over the given storage.
     *
     * @param repository student storage
     */
    public StudentService(StudentRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates and stores a new student with a generated ID.
     *
     * @param name  the name as entered
     * @param email the email as entered
     * @return the stored student, with normalized fields
     * @throws ValidationException if a field is invalid or the email is already used
     */
    public Student create(String name, String email) {
        Student student = new Student(nextId(), name, email);
        ensureEmailUnused(student.email(), student.id());
        repository.save(student);
        return student;
    }

    /**
     * Replaces the name and email of an existing student. The ID never changes.
     *
     * @param id    ID of the student to edit
     * @param name  the new name as entered
     * @param email the new email as entered; may be the student's current email
     * @return the stored, updated student
     * @throws ValidationException if no student has the ID, a field is invalid,
     *                             or the email belongs to another student
     */
    public Student edit(String id, String name, String email) {
        Student existing = get(id);
        Student updated = new Student(existing.id(), name, email);
        ensureEmailUnused(updated.email(), updated.id());
        repository.save(updated);
        return updated;
    }

    /**
     * Looks up a student by ID, ignoring surrounding whitespace.
     *
     * @param id the student ID as entered
     * @return the matching student
     * @throws ValidationException if no student has the ID
     */
    public Student get(String id) {
        String trimmed = id == null ? "" : id.trim();
        return repository.findById(trimmed)
                .orElseThrow(() -> new ValidationException("No student with ID " + trimmed + "."));
    }

    /**
     * Case-insensitive partial match on name or email. A blank query matches every student.
     *
     * @param query text to look for, ignoring surrounding whitespace
     * @return matching students, in storage order
     */
    public List<Student> search(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        // Stored emails are already lowercase, so only the name needs lowercasing.
        return repository.findAll().stream()
                .filter(s -> s.name().toLowerCase(Locale.ROOT).contains(needle)
                        || s.email().contains(needle))
                .toList();
    }

    /**
     * Rejects an email that another student already uses.
     *
     * @param email normalized email to check
     * @param ownId ID of the student being saved, whose own email does not count as a conflict
     * @throws ValidationException if a different student has the email
     */
    private void ensureEmailUnused(String email, String ownId) {
        boolean taken = repository.findAll().stream()
                .anyMatch(s -> s.email().equals(email) && !s.id().equals(ownId));
        if (taken) {
            throw new ValidationException("Email " + email + " is already used by another student.");
        }
    }

    /**
     * IDs are S001, S002, ...; the next one follows the highest existing number.
     *
     * @return the next unused student ID
     */
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
