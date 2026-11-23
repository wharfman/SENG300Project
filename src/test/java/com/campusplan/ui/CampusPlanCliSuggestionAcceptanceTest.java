/*
 * CampusPlanCliSuggestionAcceptanceTest.java
 *
 * Acceptance tests for the FR-04 end-user scenarios: asking for suggestions for a
 * draft event and reading the explanation, automated by typing operator input into
 * the CLI menu over the real seed room catalogue.
 */
package com.campusplan.ui;

import com.campusplan.application.AvailabilityService;
import com.campusplan.application.EventService;
import com.campusplan.application.RoomService;
import com.campusplan.application.SeedLoader;
import com.campusplan.application.StudentService;
import com.campusplan.application.scheduling.SchedulingService;
import com.campusplan.persistence.InMemoryAvailabilityRepository;
import com.campusplan.persistence.InMemoryEventRepository;
import com.campusplan.persistence.InMemoryStudentRepository;
import com.campusplan.persistence.JsonRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Suggestion scenarios driven through the CLI menu, with the seed room catalogue. */
class CampusPlanCliSuggestionAcceptanceTest {

    /** Names of the six students added by {@link #sixStudentsFiveFree}, S001-S006. */
    private static final List<String> NAMES =
            List.of("Ada Lovelace", "Grace Hopper", "Alan Turing", "Edsger Dijkstra", "Barbara Liskov", "Farah Haddad");

    /**
     * Types each line into the CLI as the organizer would and returns everything it printed.
     * Each session starts with no students, availability, or events.
     *
     * @param lines operator input, one entry per prompt
     * @return the complete CLI output
     */
    private static String session(List<String> lines) {
        StudentService students = new StudentService(new InMemoryStudentRepository());
        JsonRoomRepository catalogue = JsonRoomRepository.fromResource("/seed/rooms.json");
        InMemoryAvailabilityRepository availabilityStore = new InMemoryAvailabilityRepository();
        InMemoryEventRepository eventStore = new InMemoryEventRepository();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedReader in = new BufferedReader(new StringReader(String.join("\n", lines) + "\n"));
        new CampusPlanCli(students, new RoomService(catalogue),
                new AvailabilityService(availabilityStore, students), new EventService(eventStore, students),
                new SchedulingService(eventStore, catalogue, availabilityStore), SeedLoader.NONE, in,
                new PrintStream(bytes, true, StandardCharsets.UTF_8)).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /**
     * Input that adds students S001-S006, makes S001-S005 free 13:00-14:00 on 2026-11-03,
     * and creates draft E001 for all six on that day: 60 minutes, whiteboard required.
     *
     * @param minimum the event's minimum attendance
     * @return the input lines, ending just after the draft is created
     */
    private static List<String> sixStudentsFiveFree(int minimum) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < NAMES.size(); i++) {
            lines.addAll(List.of("1", NAMES.get(i), "student" + (i + 1) + "@campus.example"));
        }
        for (int i = 1; i <= 5; i++) {
            lines.addAll(List.of("7", String.format("S%03d", i), "2026-11-03", "13:00", "14:00"));
        }
        lines.addAll(List.of("10", "Project meeting", "S001, S002, S003, S004, S005, S006",
                "2026-11-03", "", "60", "whiteboard", String.valueOf(minimum)));
        return lines;
    }

    /**
     * The output printed from the last occurrence of {@code marker} up to the next menu,
     * so menu lines such as "5. List rooms" are not mistaken for results.
     *
     * @param output complete CLI output
     * @param marker text known to appear
     * @return the output from the last {@code marker} to the following menu
     */
    private static String after(String output, String marker) {
        String rest = output.substring(output.lastIndexOf(marker));
        // The menu is a text block, so its line breaks are always \n.
        int menu = rest.indexOf("CampusPlan\n");
        return menu < 0 ? rest : rest.substring(0, menu);
    }

    @Test
    @DisplayName("Given six invitees, five free, minimum four, and a whiteboard required, when the organizer asks for suggestions, then each shows the room, time, eligible count, names, and the sixth invitee's reason")
    void showsExplainedSuggestions() {
        List<String> input = sixStudentsFiveFree(4);
        input.addAll(List.of("14", "E001", "0"));

        String result = after(session(input), "Top suggestions");

        assertTrue(result.contains("Top suggestions for E001 Project meeting"));
        assertTrue(result.contains("1. R01 Maple Hall 101  2026-11-03 13:00-14:00  5 of 6 eligible"));
        assertTrue(result.contains("   Eligible: S001 Ada Lovelace, S002 Grace Hopper, S003 Alan Turing,"
                + " S004 Edsger Dijkstra, S005 Barbara Liskov"));
        assertTrue(result.contains("     S006 Farah Haddad: not available 13:00-14:00"));
        assertFalse(result.contains("Error"));
    }

    @Test
    @DisplayName("Given equally good rooms at the same time, when the organizer asks for suggestions, then rooms with fewer unused seats come first and only qualifying rooms appear")
    void listsSuggestionsInRankedOrder() {
        List<String> input = sixStudentsFiveFree(4);
        input.addAll(List.of("14", "E001", "0"));

        String result = after(session(input), "Top suggestions");

        // Whiteboard rooms seating six: R01 (6 seats), R04 (8), R02 (12), R06 (20).
        int r01 = result.indexOf("1. R01 "), r04 = result.indexOf("2. R04 "),
                r02 = result.indexOf("3. R02 "), r06 = result.indexOf("4. R06 ");
        assertTrue(r01 >= 0 && r01 < r04 && r04 < r02 && r02 < r06, result);
        assertFalse(result.contains("5. "));
        assertFalse(result.contains("R05"));
    }

    @Test
    @DisplayName("Given five eligible invitees and minimum six, when the organizer asks for suggestions, then no suggestion is shown and both rejection counts are given")
    void explainsWhenNothingQualifies() {
        List<String> input = sixStudentsFiveFree(6);
        input.addAll(List.of("14", "E001", "0"));

        String result = after(session(input), "No suggestions");

        assertTrue(result.contains("No suggestions: no room and time can host E001 Project meeting."));
        // R03 and R07 are too small and R05 has no whiteboard: 3 rooms x 27 starts.
        assertTrue(result.contains("Rejected for the room (too small, missing a feature, or already booked): 81"));
        // The other 4 rooms x 27 starts have at most five eligible.
        assertTrue(result.contains("Rejected for attendance (fewer than 6 eligible invitees): 108"));
    }

    @Test
    @DisplayName("Given no such event, when the organizer asks for suggestions, then an error is shown and the menu returns")
    void rejectsUnknownEvent() {
        String output = session(List.of("14", "E404", "0"));

        assertTrue(output.contains("Error: No event with ID E404."));
        assertTrue(output.contains("Goodbye."));
    }
}
