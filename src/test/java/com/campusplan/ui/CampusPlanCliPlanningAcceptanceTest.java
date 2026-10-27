/*
 * CampusPlanCliPlanningAcceptanceTest.java
 *
 * Acceptance tests for the FR-02 and FR-03 end-user scenarios: availability entry,
 * draft events, seed loading, and data surviving a restart. Each session runs the
 * CLI over a real data file in a temporary folder, the same way Main wires it.
 */
package com.campusplan.ui;

import com.campusplan.application.AvailabilityService;
import com.campusplan.application.EventService;
import com.campusplan.application.RoomService;
import com.campusplan.application.SeedLoader;
import com.campusplan.application.StudentService;
import com.campusplan.persistence.JsonDataStore;
import com.campusplan.persistence.JsonRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Planning and persistence scenarios driven through the CLI menu over a real data file. */
class CampusPlanCliPlanningAcceptanceTest {

    /** Small seed data set used by the seed-loading scenarios. */
    private static final String SEED = """
            {"formatVersion":1,
             "rooms":[{"id":"R01","name":"Maple Hall 101","capacity":6,"features":["WHITEBOARD"]}],
             "students":[{"id":"S001","name":"Seed Student","email":"seed@example.com"}],
             "availability":[],"events":[]}""";

    /** Fresh temporary folder for each test, deleted afterwards. */
    @TempDir
    Path dir;

    /** The data file inside {@link #dir}. */
    private Path file() {
        return dir.resolve("campusplan.json");
    }

    /**
     * Opens the data file, types each line into the CLI, and returns everything it printed.
     * Running this twice on the same file is a restart.
     *
     * @param lines operator input, one entry per prompt
     * @return the complete CLI output
     */
    private String session(String... lines) {
        JsonDataStore store = JsonDataStore.open(file(), JsonRoomRepository.fromResource("/seed/rooms.json").findAll());
        SeedLoader seed = new SeedLoader() {
            @Override
            public boolean canLoad() {
                return store.isEmpty();
            }

            @Override
            public void load() {
                store.loadSeed(new ByteArrayInputStream(SEED.getBytes(StandardCharsets.UTF_8)));
            }
        };
        StudentService students = new StudentService(store.students());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedReader in = new BufferedReader(new StringReader(String.join("\n", lines) + "\n"));
        new CampusPlanCli(students, new RoomService(store.rooms()),
                new AvailabilityService(store.availability(), students),
                new EventService(store.events(), students), seed, in,
                new PrintStream(bytes, true, StandardCharsets.UTF_8)).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** Input that declines the seed offer and adds three students, S001-S003. */
    private static final String[] THREE_STUDENTS = {
        "n",
        "1", "Ada Lovelace", "ada@example.com",
        "1", "Grace Hopper", "grace@example.com",
        "1", "Alan Turing", "alan@example.com",
    };

    /** Joins the three-student setup with further input. */
    private static String[] withStudents(String... more) {
        String[] all = new String[THREE_STUDENTS.length + more.length];
        System.arraycopy(THREE_STUDENTS, 0, all, 0, THREE_STUDENTS.length);
        System.arraycopy(more, 0, all, THREE_STUDENTS.length, more.length);
        return all;
    }

    @Test
    @DisplayName("Demo: given a new install, when the organizer adds students, is refused a duplicate email, enters availability, filters rooms, creates a draft, and restarts, then everything is still there")
    void demoSliceSurvivesRestart() {
        String first = session(withStudents(
                "1", "Copy Cat", "ADA@example.com",
                "7", "S001", "2026-11-02", "09:00", "12:00",
                "6", "6", "whiteboard",
                "10", "Project meeting", "S001, S002, S003", "2026-11-02", "2026-11-04", "120", "whiteboard", "2",
                "0"));

        assertTrue(first.contains("Error: Email ada@example.com is already used by another student."));
        assertTrue(first.contains("2026-11-02  09:00-12:00"));
        assertTrue(first.contains("Aspen Seminar Room"));
        assertTrue(first.contains("Created draft E001  DRAFT  2026-11-02 to 2026-11-04  Project meeting"));

        String second = session("4", "", "9", "S001", "12", "E001", "0");

        assertFalse(second.contains("Load sample data"));
        assertTrue(second.contains("S003  Alan Turing <alan@example.com>"));
        assertFalse(second.contains("Copy Cat"));
        assertTrue(second.contains("2026-11-02  09:00-12:00"));
        assertTrue(second.contains("Title:              Project meeting"));
        assertTrue(second.contains("Required features:  WHITEBOARD"));
        assertTrue(second.contains("PENDING"));
    }

    @Test
    @DisplayName("Given a student, when the organizer adds adjacent availability and then removes the middle, then the CLI shows the merged and split intervals")
    void addAndRemoveAvailability() {
        String output = session(withStudents(
                "7", "S001", "2026-11-02", "09:00", "10:00",
                "7", "S001", "2026-11-02", "10:00", "12:00",
                "8", "S001", "2026-11-02", "10:00", "11:00",
                "9", "S001", "0"));

        assertTrue(output.contains("2026-11-02  09:00-12:00"));
        String view = output.substring(output.lastIndexOf("Availability for S001"));
        assertTrue(view.contains("2026-11-02  09:00-10:00"));
        assertTrue(view.contains("2026-11-02  11:00-12:00"));
    }

    @Test
    @DisplayName("Given a student, when the organizer enters invalid availability, then each is rejected with a clear error and nothing is stored")
    void rejectsInvalidAvailability() {
        String output = session(withStudents(
                "7", "S001", "2026-11-02", "07:30", "09:00",
                "7", "S001", "2026-11-02", "09:15", "10:00",
                "7", "S001", "2026-11-02", "11:00", "10:00",
                "7", "S001", "2026-02-30", "09:00", "10:00",
                "7", "S001", "2026-11-02", "9am", "10:00",
                "7", "S404", "2026-11-02", "09:00", "10:00",
                "8", "S001", "2026-11-02", "09:00", "10:00",
                "9", "S001", "0"));

        assertTrue(output.contains("Error: Times must be within opening hours 08:00-22:00."));
        assertTrue(output.contains("Error: Times must be on a 30-minute boundary, such as 09:00 or 09:30."));
        assertTrue(output.contains("Error: Start time must be before end time."));
        assertTrue(output.contains("Error: Date must be a real date written YYYY-MM-DD, such as 2026-11-02."));
        assertTrue(output.contains("Error: Time must be written HH:MM in 24-hour time, such as 09:30."));
        assertTrue(output.contains("Error: No student with ID S404."));
        assertTrue(output.contains("Error: Student S001 has no availability on 2026-11-02 between 09:00 and 10:00."));
        assertTrue(output.substring(output.lastIndexOf("Availability for S001")).contains("No availability entered."));
    }

    @Test
    @DisplayName("Given students, when the organizer creates drafts that break limits or invite unknown students, then each is rejected and no event is created")
    void rejectsInvalidDrafts() {
        String output = session(withStudents(
                "10", "Solo", "S001", "2026-11-02", "", "60", "", "2",
                "10", "Ghost", "S001, S404", "2026-11-02", "", "60", "", "2",
                "10", "Twice", "S001, S001", "2026-11-02", "", "60", "", "2",
                "10", "Too long", "S001, S002", "2026-11-02", "2026-11-09", "60", "", "2",
                "10", "Odd length", "S001, S002", "2026-11-02", "", "45", "", "2",
                "10", "Too many needed", "S001, S002", "2026-11-02", "", "60", "", "3",
                "10", "Not a number", "S001, S002", "2026-11-02", "", "an hour",
                "13", "0"));

        assertTrue(output.contains("Error: An event must invite 2-20 distinct students."));
        assertTrue(output.contains("Error: No student with ID S404."));
        assertTrue(output.contains("Error: Student S001 is invited more than once."));
        assertTrue(output.contains("Error: Date window must be 1-7 consecutive days."));
        assertTrue(output.contains("Error: Duration must be 30-180 minutes in 30-minute steps."));
        assertTrue(output.contains("Error: Minimum attendance must be 2-2 (the number of invitees)."));
        assertTrue(output.contains("Error: Duration must be a whole number."));
        assertTrue(output.contains("No events."));
    }

    @Test
    @DisplayName("Given a draft, when the organizer edits it leaving some fields blank, then blank fields are kept and changed fields are stored")
    void editDraftKeepingBlankFields() {
        String output = session(withStudents(
                "10", "Study session", "S001, S002", "2026-11-02", "", "60", "projector", "2",
                "11", "E001", "Exam review", "S001, S002, S003", "", "2026-11-03", "", "", "3",
                "12", "E001", "0"));

        assertTrue(output.contains("Updated E001  DRAFT  2026-11-02 to 2026-11-03  Exam review"));
        assertTrue(output.contains("Duration:           60 minutes"));
        assertTrue(output.contains("Required features:  PROJECTOR"));
        assertTrue(output.contains("Minimum attendance: 3 of 3"));
        assertTrue(output.contains("Alan Turing"));
    }

    @Test
    @DisplayName("Given a draft, when an edit breaks a limit, then it is rejected and the draft is unchanged")
    void rejectedEditLeavesDraftUnchanged() {
        String output = session(withStudents(
                "10", "Study session", "S001, S002", "2026-11-02", "", "60", "", "2",
                "11", "E001", "", "", "", "", "200", "", "",
                "12", "E001", "0"));

        assertTrue(output.contains("Error: Duration must be 30-180 minutes in 30-minute steps."));
        assertTrue(output.contains("Duration:           60 minutes"));
    }

    @Test
    @DisplayName("Given no such event, when the organizer views or edits it, then an error is shown")
    void unknownEvent() {
        String output = session("n", "12", "E404", "11", "E404", "0");

        assertTrue(output.contains("Error: No event with ID E404."));
        assertFalse(output.contains("Editing"));
    }

    @Test
    @DisplayName("Given no data file, when the organizer accepts the sample data offer, then it is loaded and saved, and it is not offered again")
    void loadsSampleDataOnFirstStart() {
        String first = session("y", "4", "", "0");

        assertTrue(first.contains("No saved data found."));
        assertTrue(first.contains("Sample data loaded."));
        assertTrue(first.contains("S001  Seed Student <seed@example.com>"));
        assertTrue(Files.exists(file()));

        String second = session("4", "", "0");
        assertFalse(second.contains("Load sample data"));
        assertTrue(second.contains("Seed Student"));
    }

    @Test
    @DisplayName("Given no data file, when the organizer declines the sample data, then the application starts empty")
    void declinesSampleData() {
        String output = session("n", "4", "", "0");

        assertTrue(output.contains("Starting with no data."));
        assertTrue(output.contains("No students found."));
    }

    @Test
    @DisplayName("Given a corrupt data file, when the application starts, then it reports the error and leaves the file unchanged")
    void corruptFileIsNotOverwritten() throws IOException {
        Files.writeString(file(), "{ this is not json", StandardCharsets.UTF_8);
        byte[] original = Files.readAllBytes(file());

        assertThrows(com.campusplan.persistence.StorageException.class, () -> session("0"));

        assertArrayEquals(original, Files.readAllBytes(file()));
    }
}
