package com.campusplan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StudentTest {

    @Test
    @DisplayName("Given a padded name and mixed-case email, when a student is created, then both are normalized")
    void normalizesNameAndEmail() {
        Student student = new Student("S001", "  Ada Lovelace  ", "  Ada@Example.COM ");

        assertEquals("Ada Lovelace", student.name());
        assertEquals("ada@example.com", student.email());
    }

    @Test
    @DisplayName("Given a 100-character name, when a student is created, then it is accepted")
    void acceptsMaxLengthName() {
        String name = "a".repeat(100);

        assertEquals(name, new Student("S001", name, "a@b.c").name());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Given a name that is empty after trimming, when a student is created, then it is rejected")
    void rejectsBlankName(String name) {
        assertThrows(ValidationException.class, () -> new Student("S001", name, "a@b.c"));
    }

    @Test
    @DisplayName("Given a 101-character name, when a student is created, then it is rejected")
    void rejectsTooLongName() {
        assertThrows(ValidationException.class, () -> new Student("S001", "a".repeat(101), "a@b.c"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"no-at-sign", "two@@example.com", "a@b@c", "@example.com", "ada@", "ada lovelace@example.com", "ada@exa\tmple.com"})
    @DisplayName("Given a malformed email, when a student is created, then it is rejected")
    void rejectsMalformedEmail(String email) {
        assertThrows(ValidationException.class, () -> new Student("S001", "Ada", email));
    }
}
