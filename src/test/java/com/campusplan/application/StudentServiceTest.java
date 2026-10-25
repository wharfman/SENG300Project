package com.campusplan.application;

import com.campusplan.domain.Student;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.InMemoryStudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StudentServiceTest {
    private StudentService service;

    @BeforeEach
    void setUp() {
        service = new StudentService(new InMemoryStudentRepository());
    }

    @Test
    @DisplayName("Given no students, when two are created, then they get sequential generated IDs")
    void generatesSequentialIds() {
        assertEquals("S001", service.create("Ada", "ada@example.com").id());
        assertEquals("S002", service.create("Grace", "grace@example.com").id());
    }

    @Test
    @DisplayName("Given a student with an email, when another is created with the same email in different case and padding, then it is rejected and not saved")
    void rejectsDuplicateEmailOnCreate() {
        service.create("Ada", "ada@example.com");

        assertThrows(ValidationException.class, () -> service.create("Imposter", "  ADA@example.com "));
        assertEquals(1, service.search("").size());
    }

    @Test
    @DisplayName("Given a student, when their name and email are edited, then the ID is unchanged and the new values are stored")
    void editKeepsId() {
        Student ada = service.create("Ada", "ada@example.com");

        Student edited = service.edit(ada.id(), "Ada Lovelace", "lovelace@example.com");

        assertEquals(ada.id(), edited.id());
        assertEquals(edited, service.get(ada.id()));
    }

    @Test
    @DisplayName("Given a student, when edited keeping their own email, then the edit is accepted")
    void editMayKeepOwnEmail() {
        Student ada = service.create("Ada", "ada@example.com");

        assertEquals("Ada L", service.edit(ada.id(), "Ada L", "ADA@example.com").name());
    }

    @Test
    @DisplayName("Given two students, when one is edited to the other's email, then it is rejected and unchanged")
    void rejectsDuplicateEmailOnEdit() {
        service.create("Ada", "ada@example.com");
        Student grace = service.create("Grace", "grace@example.com");

        assertThrows(ValidationException.class, () -> service.edit(grace.id(), "Grace", "ada@example.com"));
        assertEquals("grace@example.com", service.get(grace.id()).email());
    }

    @Test
    @DisplayName("Given a student, when edited with an invalid name, then it is rejected and unchanged")
    void rejectsInvalidEdit() {
        Student ada = service.create("Ada", "ada@example.com");

        assertThrows(ValidationException.class, () -> service.edit(ada.id(), "  ", "ada@example.com"));
        assertEquals(ada, service.get(ada.id()));
    }

    @Test
    @DisplayName("Given no student with an ID, when it is viewed or edited, then it is rejected")
    void rejectsUnknownId() {
        assertThrows(ValidationException.class, () -> service.get("S999"));
        assertThrows(ValidationException.class, () -> service.edit("S999", "Ada", "ada@example.com"));
    }

    @Test
    @DisplayName("Given several students, when searching a partial name or email in any case, then only matches are returned")
    void searchesByPartialNameOrEmail() {
        Student ada = service.create("Ada Lovelace", "ada@example.com");
        Student grace = service.create("Grace Hopper", "ghopper@navy.example");
        service.create("Alan Turing", "alan@example.org");

        assertEquals(List.of(ada), service.search("LOVE"));
        assertEquals(List.of(grace), service.search("navy"));
        assertEquals(List.of(), service.search("nobody"));
    }
}
