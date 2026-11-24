/*
 * CampusPlanCliSchedulingAcceptanceTest.java
 *
 * Acceptance tests for the FR-05 end-user scenarios: scheduling an event from a
 * suggestion, adjacent and overlapping bookings, cancelling with confirmation, and
 * bookings surviving a restart. Each session runs the CLI over a real data file in a
 * temporary folder with the seed room catalogue.
 */
package com.campusplan.ui;

import com.campusplan.application.AvailabilityService;
import com.campusplan.application.EventService;
import com.campusplan.application.RoomService;
import com.campusplan.application.SeedLoader;
import com.campusplan.application.StudentService;
import com.campusplan.application.scheduling.SchedulingService;
import com.campusplan.persistence.JsonDataStore;
import com.campusplan.persistence.JsonRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scheduling and cancelling scenarios driven through the CLI menu over a real data file. */
class CampusPlanCliSchedulingAcceptanceTest {

    /** The date every scenario plans for. */
    private static final String DAY = "2026-11-03";

    /** Fresh temporary folder for each test, deleted afterwards. */
    @TempDir
    Path dir;

    /**
     * Opens the data file, types each line into the CLI, and returns everything it printed.
     * Running this twice on the same file is a restart.
     *
     * @param lines operator input, one entry per prompt
     * @return the complete CLI output
     */
    private String session(List<String> lines) {
        JsonDataStore store = JsonDataStore.open(dir.resolve("campusplan.json"),
                JsonRoomRepository.fromResource("/seed/rooms.json").findAll());
        StudentService students = new StudentService(store.students());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BufferedReader in = new BufferedReader(new StringReader(String.join("\n", lines) + "\n"));
        new CampusPlanCli(students, new RoomService(store.rooms()),
                new AvailabilityService(store.availability(), students),
                new EventService(store.events(), students),
                new SchedulingService(store.events(), store.rooms(), store.availability()),
                SeedLoader.NONE, in, new PrintStream(bytes, true, StandardCharsets.UTF_8)).run();
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /**
     * Input that adds students S001-S008, makes S001-S004 free 13:00-15:00, and creates draft
     * E001 "Project meeting" for S001-S004: one day, 60 minutes, no features, minimum 3.
     * The best suggestion for E001 is then R03 Birch Study Room A, which seats exactly 4, at 13:00.
     *
     * @return the input lines
     */
    private static List<String> setUp() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            lines.addAll(List.of("1", "Student " + i, "student" + i + "@campus.example"));
        }
        for (String id : List.of("S001", "S002", "S003", "S004")) {
            lines.addAll(List.of("7", id, DAY, "13:00", "15:00"));
        }
        lines.addAll(List.of("10", "Project meeting", "S001, S002, S003, S004", DAY, "", "60", "", "3"));
        return lines;
    }

    /**
     * Input that makes S005-S008 free at the given time and creates draft E002 "Study group"
     * for them: one day, 60 minutes, no features, minimum 4.
     *
     * @param start start of their free time
     * @param end   end of their free time
     * @return the input lines
     */
    private static List<String> secondEvent(String start, String end) {
        List<String> lines = new ArrayList<>();
        for (String id : List.of("S005", "S006", "S007", "S008")) {
            lines.addAll(List.of("7", id, DAY, start, end));
        }
        lines.addAll(List.of("10", "Study group", "S005, S006, S007, S008", DAY, "", "60", "", "4"));
        return lines;
    }

    /**
     * Joins input lists into one session's input.
     *
     * @param parts input lines in order
     * @return the combined lines
     */
    @SafeVarargs
    private static List<String> input(List<String>... parts) {
        List<String> lines = new ArrayList<>();
        for (List<String> part : parts) {
            lines.addAll(part);
        }
        return lines;
    }

    /**
     * The output printed from the last occurrence of {@code marker} up to the next menu.
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
    @DisplayName("Given a draft with suggestions, when the organizer picks suggestion 1, then the event is SCHEDULED in that room and time and nobody is accepted automatically")
    void schedulesChosenSuggestion() {
        String output = session(input(setUp(), List.of("15", "E001", "1", "12", "E001", "0")));

        assertTrue(output.contains("Scheduled E001 Project meeting in R03 Birch Study Room A, 2026-11-03 13:00-14:00."));
        String view = after(output, "ID:");
        assertTrue(view.contains("Status:             SCHEDULED"), view);
        assertTrue(view.contains("Room and time:      R03 Birch Study Room A, 2026-11-03 13:00-14:00"), view);
        assertFalse(view.contains("ACCEPTED"), view);
    }

    @Test
    @DisplayName("Given the suggestion list, when the organizer leaves the choice blank or types a number out of range, then the event stays a draft")
    void blankOrInvalidChoiceSchedulesNothing() {
        String output = session(input(setUp(), List.of("15", "E001", "", "15", "E001", "9", "12", "E001", "0")));

        assertTrue(output.contains("Nothing scheduled; E001 is still a draft."));
        assertTrue(output.contains("Error: Suggestion number must be 1-5."));
        assertTrue(after(output, "ID:").contains("Status:             DRAFT"));
    }

    @Test
    @DisplayName("Given E001 booked in R03 13:00-14:00, when another event is free 12:00-13:00, then R03 is offered for the adjacent hour and can be scheduled")
    void adjacentBookingIsAllowed() {
        String output = session(input(setUp(), secondEvent("12:00", "13:00"),
                List.of("15", "E001", "1", "15", "E002", "1", "0")));

        assertTrue(output.contains("1. R03 Birch Study Room A  2026-11-03 12:00-13:00  4 of 4 eligible"));
        assertTrue(output.contains("Scheduled E002 Study group in R03 Birch Study Room A, 2026-11-03 12:00-13:00."));
    }

    @Test
    @DisplayName("Given E001 booked in R03 13:00-14:00, when another event is free at the same time, then R03 is never offered and other rooms are")
    void overlappingBookingIsNotOffered() {
        String result = after(session(input(setUp(), secondEvent("13:00", "14:00"),
                List.of("15", "E001", "1", "14", "E002", "0"))), "Top suggestions for E002");

        assertFalse(result.contains("R03"), result);
        assertTrue(result.contains("1. R07 Willow Lounge  2026-11-03 13:00-14:00  4 of 4 eligible"), result);
    }

    @Test
    @DisplayName("Given a scheduled event, when the organizer schedules it again, then an error is shown and its booking is unchanged")
    void schedulingTwiceChangesNothing() {
        String output = session(input(setUp(), List.of("15", "E001", "1", "15", "E001", "12", "E001", "0")));

        assertTrue(output.contains("Error: Only DRAFT events can get suggestions; event E001 is SCHEDULED."));
        assertTrue(after(output, "ID:").contains("Room and time:      R03 Birch Study Room A, 2026-11-03 13:00-14:00"));
    }

    @Test
    @DisplayName("Given a scheduled event, when the organizer answers anything but YES, then it is not cancelled; when they type YES, then it is CANCELLED and cannot be scheduled again")
    void cancelNeedsYes() {
        String output = session(input(setUp(), List.of("15", "E001", "1",
                "16", "E001", "yes", "12", "E001",
                "16", "E001", "YES", "12", "E001", "15", "E001", "0")));

        assertTrue(output.contains("Cancel E001 Project meeting in R03 Birch Study Room A, 2026-11-03 13:00-14:00?"));
        assertTrue(output.contains("Not cancelled; nothing changed."));
        assertTrue(output.contains("Status:             SCHEDULED"));
        assertTrue(output.contains("Cancelled E001 Project meeting; R03 is free again."));
        assertTrue(output.contains("Status:             CANCELLED"));
        assertTrue(output.contains("Error: Only DRAFT events can get suggestions; event E001 is CANCELLED."));
    }

    @Test
    @DisplayName("Given a cancelled booking in R03, when another event is free at that time, then R03 is offered again")
    void cancelFreesRoom() {
        String result = after(session(input(setUp(), secondEvent("13:00", "14:00"),
                List.of("15", "E001", "1", "16", "E001", "YES", "14", "E002", "0"))), "Top suggestions for E002");

        assertTrue(result.contains("1. R03 Birch Study Room A  2026-11-03 13:00-14:00  4 of 4 eligible"), result);
    }

    @Test
    @DisplayName("Given a draft, when the organizer tries to cancel it, then an error is shown without asking for YES")
    void cancellingDraftIsRejectedBeforeAsking() {
        String output = session(input(setUp(), List.of("16", "E001", "0")));

        assertTrue(output.contains("Error: Only SCHEDULED events can be cancelled; event E001 is DRAFT."));
        assertFalse(output.contains("Type YES"));
    }

    @Test
    @DisplayName("Given a scheduled event, when CampusPlan is restarted, then the event is still SCHEDULED in the same room and time and the room is still booked")
    void bookingSurvivesRestart() {
        session(input(setUp(), List.of("15", "E001", "1", "0")));

        String output = session(input(secondEvent("13:00", "14:00"), List.of("12", "E001", "14", "E002", "0")));

        assertTrue(output.contains("Room and time:      R03 Birch Study Room A, 2026-11-03 13:00-14:00"));
        assertFalse(after(output, "Top suggestions for E002").contains("R03"));
    }
}
