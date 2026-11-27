package com.campusplan.application;

import com.campusplan.domain.Event;
import com.campusplan.domain.EventDetails;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.InMemoryEventRepository;
import com.campusplan.persistence.InMemoryStudentRepository;
import com.campusplan.domain.AttendanceResponse;
import com.campusplan.domain.EventStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RosterExportServiceTest {

    private StudentService students;
    private EventService events;
    private RosterExportService exporter;
    private InMemoryEventRepository repository;

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        students = new StudentService(new InMemoryStudentRepository());
        repository = new InMemoryEventRepository();
        events = new EventService(repository, students);
        exporter = new RosterExportService(events, students);
    }

    private Event createEvent(String... invitees) {
        return events.create(new EventDetails(
                "Meeting", List.of(invitees),
                DAY, DAY.plusDays(1), 60, Set.of(), 2));
    }

    @Test
    @DisplayName("Given an event with two invitees, when exported, then the CSV contains the correct header and roster")
    void exportsBasicRoster() throws IOException {
        students.create("Ada Lovelace", "ada@example.com");
        students.create("Grace Hopper", "grace@example.com");

        Event event = createEvent("S001", "S002");
        Path destination = tempDir.resolve("roster.csv");

        exporter.exportRoster(event.id(), destination);

        String expected = "student_id,name,email,response\r\n"
                + "S001,Ada Lovelace,ada@example.com,PENDING\r\n"
                + "S002,Grace Hopper,grace@example.com,PENDING\r\n";

        assertEquals(expected,
                Files.readString(destination, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Given a student name containing a comma, when exported, then the name is quoted")
    void escapesCommas() throws IOException {
        students.create("Smith, John", "john@example.com");
        students.create("Grace Hopper", "grace@example.com");

        Event event = createEvent("S001", "S002");
        Path destination = tempDir.resolve("roster.csv");

        exporter.exportRoster(event.id(), destination);

        String csv = Files.readString(destination, StandardCharsets.UTF_8);

        assertTrue(csv.contains(
                "S001,\"Smith, John\",john@example.com,PENDING\r\n"));
    }

    @Test
    @DisplayName("Given a student name containing quotes, when exported, then the quotes are doubled")
    void escapesQuotationMarks() throws IOException {
        students.create("John \"Jack\" Smith", "john@example.com");
        students.create("Grace Hopper", "grace@example.com");

        Event event = createEvent("S001", "S002");
        Path destination = tempDir.resolve("roster.csv");

        exporter.exportRoster(event.id(), destination);

        String csv = Files.readString(destination, StandardCharsets.UTF_8);

        assertTrue(csv.contains(
                "S001,\"John \"\"Jack\"\" Smith\",john@example.com,PENDING\r\n"));
    }

    @Test
    @DisplayName("Given student names containing line breaks, when exported, then the names are quoted")
    void escapesLineBreaks() throws IOException {
        students.create("John\nSmith", "john@example.com");
        students.create("Grace\rHopper", "grace@example.com");

        Event event = createEvent("S001", "S002");
        Path destination = tempDir.resolve("roster.csv");

        exporter.exportRoster(event.id(), destination);

        String csv = Files.readString(destination, StandardCharsets.UTF_8);

        assertTrue(csv.contains(
                "S001,\"John\nSmith\",john@example.com,PENDING\r\n"));

        assertTrue(csv.contains(
                "S002,\"Grace\rHopper\",grace@example.com,PENDING\r\n"));
    }

    @Test
    @DisplayName("Given student names containing Unicode characters, when exported, then the file uses UTF-8")
    void exportsUtf8Characters() throws IOException {
        students.create("José García", "jose@example.com");
        students.create("François Dupont", "francois@example.com");

        Event event = createEvent("S001", "S002");
        Path destination = tempDir.resolve("roster.csv");

        exporter.exportRoster(event.id(), destination);

        String expected = "student_id,name,email,response\r\n"
                + "S001,José García,jose@example.com,PENDING\r\n"
                + "S002,François Dupont,francois@example.com,PENDING\r\n";

        assertArrayEquals(
                expected.getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(destination));
    }

    @Test
    @DisplayName("Given an unknown event ID, when exported, then no file is created")
    void rejectsUnknownEvent() {
        Path destination = tempDir.resolve("roster.csv");

        assertThrows(ValidationException.class,
                () -> exporter.exportRoster("E999", destination));

        assertFalse(Files.exists(destination));
    }

    @Test
    @DisplayName("Given a nonexistent destination directory, when exported, then an exception is thrown")
    void rejectsInvalidDestination() {
        students.create("Ada Lovelace", "ada@example.com");
        students.create("Grace Hopper", "grace@example.com");

        Event event = createEvent("S001", "S002");

        Path destination = tempDir.resolve("missing").resolve("roster.csv");

        assertThrows(IOException.class,
                () -> exporter.exportRoster(event.id(), destination));

        assertFalse(Files.exists(destination));
        assertEquals(event, events.get(event.id()));
    }

    @Test
    @DisplayName("Given an existing destination file, when exported, then its contents are preserved")
    void preventsOverwritingExistingFile() throws IOException {
        students.create("Ada Lovelace", "ada@example.com");
        students.create("Grace Hopper", "grace@example.com");

        Event event = createEvent("S001", "S002");
        Path destination = tempDir.resolve("roster.csv");

        Files.writeString(destination, "Original content",
                StandardCharsets.UTF_8);

        assertThrows(IOException.class,
                () -> exporter.exportRoster(event.id(), destination));

        assertEquals("Original content",
                Files.readString(destination, StandardCharsets.UTF_8));

        assertEquals(event, events.get(event.id()));
    }

    @Test
    @DisplayName("Given invitees with different attendance responses, when exported, then all responses are preserved")
    void exportsAttendanceResponses() throws IOException {
        students.create("Ada Lovelace", "ada@example.com");
        students.create("Grace Hopper", "grace@example.com");
        students.create("Alan Turing", "alan@example.com");

        Event draft = createEvent("S001", "S002", "S003");

        Event event = new Event(
                draft.id(),
                draft.details(),
                EventStatus.DRAFT,
                Map.of(
                        "S001", AttendanceResponse.ACCEPTED,
                        "S002", AttendanceResponse.DECLINED,
                        "S003", AttendanceResponse.PENDING
                )
        );

        repository.save(event);

        Path destination = tempDir.resolve("roster.csv");

        exporter.exportRoster(event.id(), destination);

        String expected = "student_id,name,email,response\r\n"
                + "S001,Ada Lovelace,ada@example.com,ACCEPTED\r\n"
                + "S002,Grace Hopper,grace@example.com,DECLINED\r\n"
                + "S003,Alan Turing,alan@example.com,PENDING\r\n";

        assertEquals(expected,
                Files.readString(destination, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Given an existing event, when successfully exported, then application data remains unchanged")
    void preservesApplicationData() throws IOException {
        students.create("Ada Lovelace", "ada@example.com");
        students.create("Grace Hopper", "grace@example.com");

        Event event = createEvent("S001", "S002");

        var eventsBefore = events.list();
        var studentOneBefore = students.get("S001");
        var studentTwoBefore = students.get("S002");

        Path destination = tempDir.resolve("roster.csv");

        exporter.exportRoster(event.id(), destination);

        assertTrue(Files.exists(destination));
        assertEquals(eventsBefore, events.list());
        assertEquals(studentOneBefore, students.get("S001"));
        assertEquals(studentTwoBefore, students.get("S002"));
    }
}
