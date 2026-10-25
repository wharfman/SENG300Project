/*
 * Student.java
 *
 * Domain model for a student who can be invited to events, including the
 * validation rules for student names and emails.
 */
package com.campusplan.domain;

import java.util.Locale;

/**
 * A student. The name is stored trimmed and the email trimmed and lowercased,
 * so two students' emails can be compared directly.
 *
 * <p>Instances are immutable; editing a student means creating a new instance
 * with the same ID.
 *
 * @param id    generated identifier, never blank
 * @param name  display name, 1-100 characters after trimming
 * @param email contact email with exactly one {@code @}, text on both sides, and no whitespace
 */
public record Student(String id, String name, String email) {

    /** Maximum length of a student name, measured after trimming. */
    public static final int MAX_NAME_LENGTH = 100;

    /**
     * Validates and normalizes every field.
     *
     * @throws ValidationException if the ID is blank, or the name or email is invalid
     */
    public Student {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Student ID is required.");
        }
        name = validateName(name);
        email = normalizeEmail(email);
    }

    /**
     * Trims a name and checks its length.
     *
     * @param name the name as entered, possibly null
     * @return the trimmed name
     * @throws ValidationException if the trimmed name is empty or too long
     */
    private static String validateName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(
                    "Student name must be 1-" + MAX_NAME_LENGTH + " characters after trimming.");
        }
        return trimmed;
    }

    /**
     * Trims and lowercases an email and checks its format.
     *
     * @param email the email as entered, possibly null
     * @return the normalized email
     * @throws ValidationException if the email is malformed
     */
    private static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        // Valid only if there is one @, it is neither first nor last, and no whitespace remains inside.
        boolean valid = at > 0
                && at == normalized.lastIndexOf('@')
                && at < normalized.length() - 1
                && normalized.chars().noneMatch(Character::isWhitespace);
        if (!valid) {
            throw new ValidationException(
                    "Email must contain exactly one @, text on both sides, and no whitespace.");
        }
        return normalized;
    }
}
