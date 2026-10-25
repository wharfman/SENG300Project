package com.campusplan.domain;

import java.util.Locale;

/**
 * A student. The name is stored trimmed and the email trimmed and lowercased,
 * so two students' emails can be compared directly.
 */
public record Student(String id, String name, String email) {
    public static final int MAX_NAME_LENGTH = 100;

    public Student {
        if (id == null || id.isBlank()) {
            throw new ValidationException("Student ID is required.");
        }
        name = validateName(name);
        email = normalizeEmail(email);
    }

    private static String validateName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(
                    "Student name must be 1-" + MAX_NAME_LENGTH + " characters after trimming.");
        }
        return trimmed;
    }

    private static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
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
