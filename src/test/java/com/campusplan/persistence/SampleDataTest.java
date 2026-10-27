/*
 * SampleDataTest.java
 *
 * Integration tests for the shipped sample data: each file loads through JsonDataStore,
 * meets the size requirements, and has (or lacks) a valid suggestion as documented.
 */
package com.campusplan.persistence;

import com.campusplan.domain.Availability;
import com.campusplan.domain.Event;
import com.campusplan.domain.EventDetails;
import com.campusplan.domain.Room;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks that {@code sample-data/} and the bundled seed data match what the README promises. */
class SampleDataTest {

    /** Dataset where every event, including E001, has a valid suggestion. */
    private static final Path SAMPLE = Path.of("sample-data", "campusplan-sample.json");

    /** Same data, but E001 needs all six invitees, so it has no valid suggestion. */
    private static final Path NO_SUGGESTION = Path.of("sample-data", "campusplan-no-suggestion.json");

    /** Seed data bundled into the application and offered on first start. */
    private static final Path SEED = Path.of("src", "main", "resources", "seed", "campusplan-seed.json");

    /** Opens a sample file read-only; opening never writes. */
    private static JsonDataStore open(Path file) {
        return JsonDataStore.open(file, List.of());
    }

    /**
     * Test oracle, not the P2 scheduler: the most invitees available for any room and
     * 30-minute start in the event's window, among rooms that seat every invitee and have
     * every required feature. There are no scheduled events in P1, so no conflicts apply.
     *
     * @param store the loaded data
     * @param event the event to check
     * @return the best eligible count, or -1 if no room qualifies
     */
    private static int bestEligibleCount(JsonDataStore store, Event event) {
        EventDetails d = event.details();
        Map<String, Availability> availability = store.availability().findAll().stream()
                .collect(Collectors.toMap(Availability::studentId, Function.identity()));
        List<Room> rooms = store.rooms().findAll().stream()
                .filter(r -> r.capacity() >= d.inviteeIds().size() && r.hasFeatures(d.requiredFeatures()))
                .toList();
        if (rooms.isEmpty()) {
            return -1;
        }
        int best = 0;
        for (LocalDate day = d.firstDay(); !day.isAfter(d.lastDay()); day = day.plusDays(1)) {
            // Bound by the last possible start, since LocalTime wraps past midnight.
            LocalTime lastStart = LocalTime.of(22, 0).minusMinutes(d.durationMinutes());
            for (LocalTime start = LocalTime.of(8, 0); !start.isAfter(lastStart); start = start.plusMinutes(30)) {
                LocalDate date = day;
                LocalTime from = start;
                LocalTime to = start.plusMinutes(d.durationMinutes());
                int eligible = (int) d.inviteeIds().stream()
                        .filter(id -> availability.containsKey(id) && availability.get(id).isAvailable(date, from, to))
                        .count();
                best = Math.max(best, eligible);
            }
        }
        return best;
    }

    @Test
    @DisplayName("Given the sample file, when loaded, then it has at least 12 students, 6 varied rooms, 7 days of availability, and 3 events")
    void sampleMeetsSizeRequirements() {
        JsonDataStore store = open(SAMPLE);

        assertTrue(store.students().findAll().size() >= 12);
        List<Room> rooms = store.rooms().findAll();
        assertTrue(rooms.size() >= 6);
        assertTrue(rooms.stream().map(Room::capacity).distinct().count() > 1);
        assertTrue(rooms.stream().map(Room::features).distinct().count() > 1);
        long days = store.availability().findAll().stream()
                .flatMap(a -> a.intervals().stream())
                .map(i -> i.date())
                .distinct().count();
        assertEquals(7, days);
        assertTrue(store.events().findAll().size() >= 3);
    }

    @Test
    @DisplayName("Given the sample file, when E001 is checked, then a room and time exist where five of six invitees can attend, meeting minimum 4")
    void sampleHasValidSuggestion() {
        JsonDataStore store = open(SAMPLE);
        Event capstone = store.events().findById("E001").orElseThrow();

        assertEquals(4, capstone.details().minimumAttendance());
        assertEquals(5, bestEligibleCount(store, capstone));
        for (Event event : store.events().findAll()) {
            assertTrue(bestEligibleCount(store, event) >= event.details().minimumAttendance(), event.id());
        }
    }

    @Test
    @DisplayName("Given the no-suggestion file, when E001 is checked, then at most five of six can attend, below minimum 6, so nothing qualifies")
    void noSuggestionFileHasNone() {
        JsonDataStore store = open(NO_SUGGESTION);
        Event capstone = store.events().findById("E001").orElseThrow();

        assertEquals(6, capstone.details().minimumAttendance());
        assertEquals(5, bestEligibleCount(store, capstone));
        assertFalse(bestEligibleCount(store, capstone) >= capstone.details().minimumAttendance());
    }

    @Test
    @DisplayName("Given the bundled seed data, when compared with the sample file, then they are identical")
    void seedMatchesSample() throws IOException {
        assertArrayEquals(Files.readAllBytes(SAMPLE), Files.readAllBytes(SEED));
    }
}
