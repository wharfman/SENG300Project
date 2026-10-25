/*
 * CampusPlanCliAcceptanceTest.java
 *
 * Acceptance tests for the FR-01 end-user scenarios, automated by typing
 * operator input into the CLI menu and checking what it prints.
 */
package com.campusplan.ui;

import com.campusplan.application.RoomService;
import com.campusplan.application.StudentService;
import com.campusplan.persistence.InMemoryStudentRepository;
import com.campusplan.persistence.JsonRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-user scenarios driven through the CLI menu, with the real seed room catalogue. */
class CampusPlanCliAcceptanceTest {

    /**
     * Types each line into the CLI as the organizer would and returns everything it printed.
     * Each session starts with no students; input ending quits the CLI.
     *
     * @param lines operator input, one entry per prompt
     * @return the complete CLI output
     */
    private static String session(String... lines) {
        StudentService students = new StudentService(new InMemoryStudentRepository());
        RoomService rooms = new RoomService(JsonRoomRepository.fromResource("/seed/rooms.json"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedReader in = new BufferedReader(new StringReader(String.join("\n", lines) + "\n"));
        new CampusPlanCli(students, rooms, in, new PrintStream(bytes, true, StandardCharsets.UTF_8)).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /**
     * The output printed after the last occurrence of {@code marker}, used to check
     * only the result of the final matching step.
     *
     * @param output complete CLI output
     * @param marker text known to appear, typically a prompt
     * @return the output from the last {@code marker} onward
     */
    private static String after(String output, String marker) {
        return output.substring(output.lastIndexOf(marker));
    }

    @Test
    @DisplayName("Given the menu, when the organizer adds a student, then the student is created with a generated ID and normalized email")
    void addStudent() {
        String output = session("1", "  Ada Lovelace ", "Ada@Example.com", "0");

        assertTrue(output.contains("Created S001  Ada Lovelace <ada@example.com>"));
    }

    @Test
    @DisplayName("Given the menu, when the organizer enters an invalid name or email, then an error is shown and no student is created")
    void addStudentWithInvalidFields() {
        String output = session("1", "   ", "ada@example.com", "1", "Ada", "ada.example.com", "4", "", "0");

        assertTrue(output.contains("Error: Student name must be 1-100 characters after trimming."));
        assertTrue(output.contains("Error: Email must contain exactly one @, text on both sides, and no whitespace."));
        assertTrue(after(output, "Name or email contains").contains("No students found."));
    }

    @Test
    @DisplayName("Given an existing student, when the organizer adds another with the same email in different case, then it is rejected")
    void addStudentWithDuplicateEmail() {
        String output = session("1", "Ada", "ada@example.com", "1", "Other", " ADA@example.com ", "4", "", "0");

        assertTrue(output.contains("Error: Email ada@example.com is already used by another student."));
        assertFalse(after(output, "Name or email contains").contains("Other"));
    }

    @Test
    @DisplayName("Given a student, when the organizer edits leaving the name blank, then the name is kept and the email changes")
    void editStudentKeepingBlankFields() {
        String output = session("1", "Ada", "ada@example.com", "2", "S001", "", "lovelace@example.com", "3", "S001", "0");

        assertTrue(output.contains("Updated S001  Ada <lovelace@example.com>"));
        assertTrue(output.contains("Email: lovelace@example.com"));
    }

    @Test
    @DisplayName("Given two students, when the organizer edits one to the other's email, then it is rejected and unchanged")
    void editStudentToDuplicateEmail() {
        String output = session("1", "Ada", "ada@example.com", "1", "Grace", "grace@example.com",
                "2", "S002", "", "ada@example.com", "3", "S002", "0");

        assertTrue(output.contains("Error: Email ada@example.com is already used by another student."));
        assertTrue(output.contains("Email: grace@example.com"));
    }

    @Test
    @DisplayName("Given no such student, when the organizer views or edits an unknown ID, then an error is shown")
    void unknownStudentId() {
        String output = session("3", "S404", "2", "S404", "0");

        assertTrue(output.contains("Error: No student with ID S404."));
        assertFalse(output.contains("Editing"));
    }

    @Test
    @DisplayName("Given several students, when the organizer searches a partial name or email, then only matches are listed")
    void searchStudents() {
        String output = session("1", "Ada Lovelace", "ada@example.com", "1", "Grace Hopper", "grace@navy.example",
                "4", "NAVY", "4", "zzz", "0");

        String firstSearch = output.substring(output.indexOf("Name or email contains"),
                output.lastIndexOf("Name or email contains"));
        assertTrue(firstSearch.contains("S002  Grace Hopper"));
        assertFalse(firstSearch.contains("S001  Ada Lovelace"));
        assertTrue(after(output, "Name or email contains").contains("No students found."));
    }

    @Test
    @DisplayName("Given the seed catalogue, when the organizer lists rooms, then every room is shown with capacity and features")
    void listRooms() {
        String output = session("5", "0");

        assertTrue(output.contains("Maple Hall 101"));
        assertTrue(output.contains("Willow Lounge"));
        assertTrue(output.contains("WHITEBOARD, PROJECTOR, ACCESSIBLE"));
    }

    @Test
    @DisplayName("Given the seed catalogue, when the organizer filters for six seats and a whiteboard, then only qualifying rooms are shown")
    void filterRooms() {
        String result = after(session("6", "6", "whiteboard", "0"), "Required features");

        assertTrue(result.contains("Maple Hall 101"));
        assertTrue(result.contains("Aspen Seminar Room"));
        assertFalse(result.contains("Birch Study Room A"));
        assertFalse(result.contains("Cedar Lecture Room"));
    }

    @Test
    @DisplayName("Given the seed catalogue, when the organizer filters beyond every room, then no rooms match")
    void filterRoomsWithNoMatch() {
        assertTrue(session("6", "41", "", "0").contains("No rooms match."));
    }

    @Test
    @DisplayName("Given the filter prompts, when the organizer enters a bad capacity or unknown feature, then an error is shown")
    void filterRoomsWithInvalidInput() {
        assertTrue(session("6", "lots", "0").contains("Error: Minimum capacity must be a whole number."));
        assertTrue(session("6", "0", "", "0").contains("Error: Minimum capacity must be at least 1."));
        assertTrue(session("6", "", "sauna", "0").contains("Error: Unknown room feature: sauna."));
    }

    @Test
    @DisplayName("Given the menu, when the organizer picks an unknown option or input ends, then the CLI reports it and exits cleanly")
    void unknownOptionAndEndOfInput() {
        String output = session("9");

        assertTrue(output.contains("Unknown option: 9"));
        assertTrue(output.contains("Goodbye."));
    }
}
