/*
 * SchedulingServiceTest.java
 *
 * Unit tests for SchedulingService.suggest: the brief's section 3 acceptance
 * examples, the ranking and tie-break order, opening-hour boundaries, the
 * no-result counts, and rejected searches.
 */
package com.campusplan.application.scheduling;

import com.campusplan.domain.Availability;
import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.EventDetails;
import com.campusplan.domain.EventStatus;
import com.campusplan.domain.Room;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.InMemoryAvailabilityRepository;
import com.campusplan.persistence.InMemoryEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.campusplan.application.scheduling.SchedulingFixtures.DAY;
import static com.campusplan.application.scheduling.SchedulingFixtures.scheduled;
import static com.campusplan.application.scheduling.SchedulingFixtures.slot;
import static com.campusplan.domain.AttendanceResponse.ACCEPTED;
import static com.campusplan.domain.AttendanceResponse.PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link SchedulingService#suggest} over in-memory storage. */
class SchedulingServiceTest {

    /** 60-minute starts in one day: 08:00 to 21:00 in 30-minute steps. */
    private static final int STARTS_PER_DAY = 27;

    /** Event storage shared by the service and the test. */
    private final InMemoryEventRepository events = new InMemoryEventRepository();

    /** Availability storage shared by the service and the test. */
    private final InMemoryAvailabilityRepository availability = new InMemoryAvailabilityRepository();

    /**
     * A service whose catalogue holds exactly the given rooms, in that order.
     *
     * @param catalogue the rooms
     * @return the service under test
     */
    private SchedulingService service(Room... catalogue) {
        return new SchedulingService(events, () -> List.of(catalogue), availability);
    }

    /**
     * Stores draft E001, a 60-minute event with no required features.
     *
     * @param invitees  invitee IDs
     * @param minimum   minimum attendance
     * @param firstDay  first day of the window
     * @param lastDay   last day of the window
     */
    private void storeDraft(List<String> invitees, int minimum, LocalDate firstDay, LocalDate lastDay) {
        events.save(Event.draft("E001", new EventDetails("Project meeting", invitees, firstDay, lastDay,
                60, Set.of(), minimum)));
    }

    /**
     * Records the same free time for each student.
     *
     * @param interval the free time
     * @param students student IDs
     */
    private void free(AvailabilityInterval interval, String... students) {
        for (String student : students) {
            Availability current = availability.findByStudentId(student).orElse(Availability.empty(student));
            availability.save(current.with(interval));
        }
    }

    /**
     * Room ID and start of each suggestion, for checking order.
     *
     * @param result a search result
     * @return entries such as {@code "R01 2026-11-03 13:00"}
     */
    private static List<String> order(SuggestionResult result) {
        return result.top().stream()
                .map(s -> s.roomId() + " " + s.slot().date() + " " + s.slot().start())
                .toList();
    }

    @Test
    @DisplayName("Given six invitees, five free 13:00-14:00, minimum four, and a room for six, when suggesting, then that candidate lists the five and the reason the sixth cannot attend")
    void fiveOfSixEligible() {
        storeDraft(List.of("S001", "S002", "S003", "S004", "S005", "S006"), 4, DAY, DAY);
        free(slot("13:00", "14:00"), "S001", "S002", "S003", "S004", "S005");
        List<Event> before = events.findAll();

        SuggestionResult result = service(new Room("R01", "Lab", 6, Set.of())).suggest(" E001 ");

        assertEquals(1, result.top().size());
        Suggestion only = result.top().get(0);
        assertEquals("R01", only.roomId());
        assertEquals(slot("13:00", "14:00"), only.slot());
        assertEquals(List.of("S001", "S002", "S003", "S004", "S005"), only.eligible());
        assertEquals(Map.of("S006", "not available 13:00-14:00"), only.ineligibleReasons());
        assertEquals(before, events.findAll());
    }

    @Test
    @DisplayName("Given five eligible invitees and minimum six, when suggesting, then the candidate is excluded for attendance")
    void minimumAttendanceNotMet() {
        storeDraft(List.of("S001", "S002", "S003", "S004", "S005", "S006"), 6, DAY, DAY);
        free(slot("13:00", "14:00"), "S001", "S002", "S003", "S004", "S005");

        SuggestionResult result = service(new Room("R01", "Lab", 6, Set.of())).suggest("E001");

        assertEquals(List.of(), result.top());
        assertEquals(0, result.rejectedForRoom());
        assertEquals(STARTS_PER_DAY, result.rejectedForAttendance());
    }

    @Test
    @DisplayName("Given a room holding five and six invitees of whom five are eligible, when suggesting, then only the room for six is suggested")
    void roomTooSmallForInvitees() {
        storeDraft(List.of("S001", "S002", "S003", "S004", "S005", "S006"), 4, DAY, DAY);
        free(slot("13:00", "14:00"), "S001", "S002", "S003", "S004", "S005");

        SuggestionResult tooSmallOnly = service(new Room("R01", "Den", 5, Set.of())).suggest("E001");
        SuggestionResult withBigger = service(new Room("R01", "Den", 5, Set.of()),
                new Room("R02", "Lab", 6, Set.of())).suggest("E001");

        assertEquals(List.of(), tooSmallOnly.top());
        assertEquals(STARTS_PER_DAY, tooSmallOnly.rejectedForRoom());
        assertEquals(0, tooSmallOnly.rejectedForAttendance());
        assertEquals(List.of("R02 2026-11-03 13:00"), order(withBigger));
    }

    @Test
    @DisplayName("Given the room booked 12:00-13:00 and 14:00-15:00, when suggesting, then 13:00-14:00 is allowed and every overlapping start is rejected for the room")
    void adjacentReservationsAllowed() {
        events.save(scheduled("E101", "R01", slot("12:00", "13:00"), Map.of("S101", PENDING, "S102", PENDING)));
        events.save(scheduled("E102", "R01", slot("14:00", "15:00"), Map.of("S101", PENDING, "S102", PENDING)));
        storeDraft(List.of("S001", "S002"), 2, DAY, DAY);
        free(slot("12:00", "15:00"), "S001", "S002");

        SuggestionResult result = service(new Room("R01", "Lab", 6, Set.of())).suggest("E001");

        assertEquals(List.of("R01 2026-11-03 13:00"), order(result));
        // Starts 11:30, 12:00, 12:30, 13:30, 14:00 and 14:30 each overlap a booking.
        assertEquals(6, result.rejectedForRoom());
    }

    @Test
    @DisplayName("Given a student who accepted 13:00-14:00 elsewhere, when suggesting, then they are ineligible for overlapping times and eligible from 14:00")
    void acceptedElsewhereExcludesOverlappingTimes() {
        events.save(scheduled("E101", "R02", slot("13:00", "14:00"), Map.of("S001", ACCEPTED, "S101", PENDING)));
        storeDraft(List.of("S001", "S002", "S003"), 2, DAY, DAY);
        free(slot("13:00", "15:00"), "S001", "S002", "S003");

        SuggestionResult result = service(new Room("R01", "Lab", 6, Set.of())).suggest("E001");

        assertEquals(List.of("R01 2026-11-03 14:00", "R01 2026-11-03 13:00", "R01 2026-11-03 13:30"), order(result));
        assertEquals(List.of("S001", "S002", "S003"), result.top().get(0).eligible());
        assertEquals(Map.of("S001", "already accepted E101"), result.top().get(1).ineligibleReasons());
        assertEquals(Map.of("S001", "already accepted E101"), result.top().get(2).ineligibleReasons());
    }

    @Test
    @DisplayName("Given an invitee who is not free and has accepted an overlapping event, when suggesting, then their reason gives both")
    void ineligibleReasonListsEveryFailedRule() {
        events.save(scheduled("E101", "R02", slot("13:00", "14:00"), Map.of("S003", ACCEPTED, "S101", PENDING)));
        storeDraft(List.of("S001", "S002", "S003"), 2, DAY, DAY);
        free(slot("13:00", "14:00"), "S001", "S002");

        SuggestionResult result = service(new Room("R01", "Lab", 6, Set.of())).suggest("E001");

        assertEquals(Map.of("S003", "not available 13:00-14:00; already accepted E101"),
                result.top().get(0).ineligibleReasons());
    }

    @Test
    @DisplayName("Given candidates differing in eligible count, date, unused seats and room ID, when suggesting, then they follow the published order and only five are shown")
    void ranksByPublishedOrderAndKeepsFive() {
        LocalDate nextDay = DAY.plusDays(1);
        AvailabilityInterval firstMorning = slot("09:00", "10:00");
        AvailabilityInterval secondMorning = new AvailabilityInterval(nextDay, LocalTime.of(9, 0), LocalTime.of(10, 0));
        storeDraft(List.of("S001", "S002", "S003"), 2, DAY, nextDay);
        free(firstMorning, "S001", "S002");
        free(secondMorning, "S001", "S002", "S003");

        // Catalogue order differs from the expected order, so sorting is what decides it.
        SuggestionResult result = service(new Room("R01", "Lab", 4, Set.of()),
                new Room("R03", "Den", 3, Set.of()), new Room("R02", "Hub", 3, Set.of())).suggest("E001");

        // Three eligible on the second day beats two on the first; then fewer unused seats; then room ID.
        assertEquals(List.of("R02 2026-11-04 09:00", "R03 2026-11-04 09:00", "R01 2026-11-04 09:00",
                "R02 2026-11-03 09:00", "R03 2026-11-03 09:00"), order(result));
    }

    @Test
    @DisplayName("Given candidates with the same eligible count in one room, when suggesting, then earlier times come first")
    void equalCountsRankByTime() {
        storeDraft(List.of("S001", "S002"), 2, DAY, DAY);
        free(slot("09:00", "11:00"), "S001", "S002");

        SuggestionResult result = service(new Room("R01", "Lab", 6, Set.of())).suggest("E001");

        assertEquals(List.of("R01 2026-11-03 09:00", "R01 2026-11-03 09:30", "R01 2026-11-03 10:00"), order(result));
    }

    @Test
    @DisplayName("Given tied candidates, when the search is repeated ten times, then every search returns the same order")
    void repeatedSearchesReturnSameOrder() {
        storeDraft(List.of("S001", "S002"), 2, DAY, DAY);
        free(slot("09:00", "12:00"), "S001", "S002");
        SchedulingService service = service(new Room("R02", "Hub", 6, Set.of()), new Room("R01", "Lab", 6, Set.of()));
        SuggestionResult first = service.suggest("E001");

        List<SuggestionResult> repeats = new ArrayList<>();
        for (int run = 0; run < 10; run++) {
            repeats.add(service.suggest("E001"));
        }

        assertEquals(5, first.top().size());
        repeats.forEach(repeat -> assertEquals(first, repeat));
    }

    @Test
    @DisplayName("Given free time at opening and at closing, when suggesting, then 08:00-09:00 and 21:00-22:00 are suggested and no later start is tried")
    void searchesWholeOpeningHours() {
        storeDraft(List.of("S001", "S002"), 2, DAY, DAY);
        free(slot("08:00", "09:00"), "S001", "S002");
        free(slot("21:00", "22:00"), "S001", "S002");

        SuggestionResult result = service(new Room("R01", "Lab", 6, Set.of())).suggest("E001");

        assertEquals(List.of("R01 2026-11-03 08:00", "R01 2026-11-03 21:00"), order(result));
        assertEquals(STARTS_PER_DAY - 2, result.rejectedForAttendance());
    }

    @Test
    @DisplayName("Given one room too small and one room where nobody is free, when suggesting, then nothing qualifies and room failures are counted first without overlap")
    void noResultCountsRoomFailuresFirst() {
        storeDraft(List.of("S001", "S002", "S003"), 2, DAY, DAY);

        SuggestionResult result = service(new Room("R01", "Den", 2, Set.of()),
                new Room("R02", "Lab", 6, Set.of())).suggest("E001");

        assertEquals(List.of(), result.top());
        // R01 also has nobody eligible, but a room failure is counted only as a room failure.
        assertEquals(STARTS_PER_DAY, result.rejectedForRoom());
        assertEquals(STARTS_PER_DAY, result.rejectedForAttendance());
    }

    @Test
    @DisplayName("Given an unknown event ID, when suggesting, then it is rejected")
    void rejectsUnknownEvent() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service(new Room("R01", "Lab", 6, Set.of())).suggest("E404"));

        assertEquals("No event with ID E404.", e.getMessage());
    }

    @Test
    @DisplayName("Given a SCHEDULED or CANCELLED event, when suggesting, then it is rejected")
    void rejectsEventsThatAreNotDrafts() {
        events.save(scheduled("E101", "R01", slot("13:00", "14:00"), Map.of("S001", PENDING, "S002", PENDING)));
        events.save(new Event("E102", new EventDetails("Old", List.of("S001", "S002"), DAY, DAY, 60, Set.of(), 2),
                EventStatus.CANCELLED, Map.of("S001", PENDING, "S002", PENDING)));
        SchedulingService service = service(new Room("R01", "Lab", 6, Set.of()));

        ValidationException scheduled = assertThrows(ValidationException.class, () -> service.suggest("E101"));
        ValidationException cancelled = assertThrows(ValidationException.class, () -> service.suggest("E102"));

        assertEquals("Only DRAFT events can get suggestions; event E101 is SCHEDULED.", scheduled.getMessage());
        assertTrue(cancelled.getMessage().endsWith("event E102 is CANCELLED."));
    }
}
