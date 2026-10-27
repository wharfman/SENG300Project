/*
 * CampusPlanCli.java
 *
 * User-interface layer: the interactive, menu-driven command-line interface through
 * which the organizer runs every CampusPlan workflow.
 */
package com.campusplan.ui;

import com.campusplan.application.AvailabilityService;
import com.campusplan.application.EventService;
import com.campusplan.application.RoomService;
import com.campusplan.application.SeedLoader;
import com.campusplan.application.StudentService;
import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.EventDetails;
import com.campusplan.domain.Room;
import com.campusplan.domain.RoomFeature;
import com.campusplan.domain.Student;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.StorageException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Menu-driven text interface. Reaching end of input quits.
 *
 * <p>Input and output are injected so the CLI can be driven by tests. Rule violations and
 * storage failures are reported as {@code Error: ...} lines and the menu is shown again;
 * they never end the session. Every change is saved by the storage layer as it happens.
 */
public class CampusPlanCli {

    /** Main menu, printed before every prompt for an option. */
    private static final String MENU = """

            CampusPlan
              1. Add student
              2. Edit student
              3. View student
              4. Search students
              5. List rooms
              6. Filter rooms
              7. Add availability
              8. Remove availability
              9. View availability
             10. Create draft event
             11. Edit draft event
             12. View event
             13. List events
              0. Quit""";

    /** Student workflows. */
    private final StudentService students;
    /** Room catalogue queries. */
    private final RoomService rooms;
    /** Availability workflows. */
    private final AvailabilityService availability;
    /** Draft event workflows. */
    private final EventService events;
    /** Offers sample data when the application starts empty. */
    private final SeedLoader seed;
    /** Operator input, read one line per prompt. */
    private final BufferedReader in;
    /** Prompts and results. */
    private final PrintStream out;

    /**
     * Creates a CLI over the given services and streams.
     *
     * @param students     student workflows
     * @param rooms        room catalogue queries
     * @param availability availability workflows
     * @param events       draft event workflows
     * @param seed         seed data offered at startup; {@link SeedLoader#NONE} to never offer it
     * @param in           operator input
     * @param out          destination for prompts and results
     */
    public CampusPlanCli(StudentService students, RoomService rooms, AvailabilityService availability,
                         EventService events, SeedLoader seed, BufferedReader in, PrintStream out) {
        this.students = students;
        this.rooms = rooms;
        this.availability = availability;
        this.events = events;
        this.seed = seed;
        this.in = in;
        this.out = out;
    }

    /** Offers seed data if the application is empty, then handles menu choices until the operator quits. */
    public void run() {
        offerSeed();
        while (true) {
            out.println(MENU);
            String choice = prompt("Choose an option");
            if (choice == null || choice.equals("0")) {
                out.println("Goodbye.");
                return;
            }
            try {
                switch (choice) {
                    case "1" -> addStudent();
                    case "2" -> editStudent();
                    case "3" -> viewStudent();
                    case "4" -> printStudents(students.search(promptOrEmpty("Name or email contains")));
                    case "5" -> printRooms(rooms.list());
                    case "6" -> filterRooms();
                    case "7" -> changeAvailability(true);
                    case "8" -> changeAvailability(false);
                    case "9" -> viewAvailability();
                    case "10" -> createEvent();
                    case "11" -> editEvent();
                    case "12" -> viewEvent();
                    case "13" -> listEvents();
                    default -> out.println("Unknown option: " + choice);
                }
            } catch (ValidationException | StorageException e) {
                out.println("Error: " + e.getMessage());
            }
        }
    }

    /** Asks whether to load sample data when there is no saved data. */
    private void offerSeed() {
        if (!seed.canLoad()) {
            return;
        }
        out.println("No saved data found.");
        String answer = promptOrEmpty("Load sample data? (y/n)");
        if (!answer.equalsIgnoreCase("y")) {
            out.println("Starting with no data.");
            return;
        }
        try {
            seed.load();
            out.println("Sample data loaded.");
        } catch (ValidationException | StorageException e) {
            out.println("Error: " + e.getMessage());
        }
    }

    /** Prompts for a name and email and creates the student. */
    private void addStudent() {
        String name = promptOrEmpty("Name");
        String email = promptOrEmpty("Email");
        out.println("Created " + format(students.create(name, email)));
    }

    /** Prompts for a student ID, then for new values; a blank answer keeps the current value. */
    private void editStudent() {
        Student existing = students.get(promptOrEmpty("Student ID"));
        out.println("Editing " + format(existing) + " (leave blank to keep current value)");
        String name = promptOrEmpty("Name [" + existing.name() + "]");
        String email = promptOrEmpty("Email [" + existing.email() + "]");
        // A blank name or email is never valid, so blank can safely mean "unchanged".
        Student updated = students.edit(existing.id(),
                name.isBlank() ? existing.name() : name,
                email.isBlank() ? existing.email() : email);
        out.println("Updated " + format(updated));
    }

    /** Prompts for a student ID and prints that student's details. */
    private void viewStudent() {
        Student student = students.get(promptOrEmpty("Student ID"));
        out.println("ID:    " + student.id());
        out.println("Name:  " + student.name());
        out.println("Email: " + student.email());
    }

    /** Prompts for a minimum capacity and required features and prints the matching rooms. */
    private void filterRooms() {
        String capacityInput = promptOrEmpty("Minimum capacity (blank for any)");
        int minCapacity = capacityInput.isBlank() ? 1 : parseCapacity(capacityInput);
        String featureInput = promptOrEmpty("Required features, comma-separated "
                + List.of(RoomFeature.values()) + " (blank for none)");
        printRooms(rooms.filter(minCapacity, parseFeatures(featureInput)));
    }

    /**
     * Prompts for a student and a time block, then adds or removes it.
     *
     * @param add true to add the time, false to remove it
     */
    private void changeAvailability(boolean add) {
        Student student = students.get(promptOrEmpty("Student ID"));
        LocalDate date = parseDate(promptOrEmpty("Date (YYYY-MM-DD)"));
        LocalTime start = parseTime(promptOrEmpty("Start time (HH:MM)"));
        LocalTime end = parseTime(promptOrEmpty("End time (HH:MM)"));
        List<AvailabilityInterval> result = add
                ? availability.add(student.id(), date, start, end)
                : availability.remove(student.id(), date, start, end);
        out.println((add ? "Added" : "Removed") + " availability for " + student.id() + ".");
        printAvailability(result);
    }

    /** Prompts for a student ID and prints their availability. */
    private void viewAvailability() {
        Student student = students.get(promptOrEmpty("Student ID"));
        out.println("Availability for " + format(student) + ":");
        printAvailability(availability.get(student.id()));
    }

    /** Prompts for every planning field and creates a draft event. */
    private void createEvent() {
        String title = promptOrEmpty("Title");
        List<String> invitees = parseIds(promptOrEmpty("Invitee student IDs, comma-separated"));
        LocalDate firstDay = parseDate(promptOrEmpty("First day (YYYY-MM-DD)"));
        String lastInput = promptOrEmpty("Last day (YYYY-MM-DD, blank for same day)");
        LocalDate lastDay = lastInput.isBlank() ? firstDay : parseDate(lastInput);
        int duration = parseNumber(promptOrEmpty("Duration in minutes"), "Duration");
        Set<RoomFeature> features = parseFeatures(promptOrEmpty("Required features, comma-separated "
                + List.of(RoomFeature.values()) + " (blank for none)"));
        int minimum = parseNumber(promptOrEmpty("Minimum attendance"), "Minimum attendance");
        Event event = events.create(new EventDetails(title, invitees, firstDay, lastDay, duration, features, minimum));
        out.println("Created draft " + format(event));
    }

    /** Prompts for an event ID, then for new planning fields; a blank answer keeps the current value. */
    private void editEvent() {
        Event existing = events.get(promptOrEmpty("Event ID"));
        EventDetails d = existing.details();
        out.println("Editing " + format(existing) + " (leave blank to keep current value)");
        String title = promptOrEmpty("Title [" + d.title() + "]");
        String invitees = promptOrEmpty("Invitee student IDs [" + String.join(", ", d.inviteeIds()) + "]");
        String firstDay = promptOrEmpty("First day [" + d.firstDay() + "]");
        String lastDay = promptOrEmpty("Last day [" + d.lastDay() + "]");
        String duration = promptOrEmpty("Duration in minutes [" + d.durationMinutes() + "]");
        String features = promptOrEmpty("Required features [" + formatFeatures(d.requiredFeatures())
                + "] (type NONE to clear)");
        String minimum = promptOrEmpty("Minimum attendance [" + d.minimumAttendance() + "]");
        EventDetails updated = new EventDetails(
                title.isBlank() ? d.title() : title,
                invitees.isBlank() ? d.inviteeIds() : parseIds(invitees),
                firstDay.isBlank() ? d.firstDay() : parseDate(firstDay),
                lastDay.isBlank() ? d.lastDay() : parseDate(lastDay),
                duration.isBlank() ? d.durationMinutes() : parseNumber(duration, "Duration"),
                // Blank keeps the features, so clearing them needs an explicit word.
                features.isBlank() ? d.requiredFeatures()
                        : features.equalsIgnoreCase("NONE") ? Set.of() : parseFeatures(features),
                minimum.isBlank() ? d.minimumAttendance() : parseNumber(minimum, "Minimum attendance"));
        out.println("Updated " + format(events.edit(existing.id(), updated)));
    }

    /** Prompts for an event ID and prints its details and each invitee's response. */
    private void viewEvent() {
        Event event = events.get(promptOrEmpty("Event ID"));
        EventDetails d = event.details();
        out.println("ID:                 " + event.id());
        out.println("Title:              " + d.title());
        out.println("Status:             " + event.status());
        out.println("Date window:        " + d.firstDay() + " to " + d.lastDay() + " (" + d.windowDays() + " days)");
        out.println("Duration:           " + d.durationMinutes() + " minutes");
        out.println("Required features:  " + formatFeatures(d.requiredFeatures()));
        out.println("Minimum attendance: " + d.minimumAttendance() + " of " + d.inviteeIds().size());
        out.println("Invitees:");
        event.responses().forEach((id, response) ->
                out.printf("  %-5s %-30s %s%n", id, students.get(id).name(), response));
    }

    /** Prints one line per event, or a message if there are none. */
    private void listEvents() {
        List<Event> all = events.list();
        if (all.isEmpty()) {
            out.println("No events.");
            return;
        }
        all.forEach(e -> out.println(format(e)));
    }

    /**
     * Parses a capacity typed by the operator.
     *
     * @param input trimmed, non-blank text
     * @return the number entered
     * @throws ValidationException if the text is not a whole number
     */
    private static int parseCapacity(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new ValidationException("Minimum capacity must be a whole number.");
        }
    }

    /**
     * Parses a whole number typed by the operator.
     *
     * @param input trimmed text
     * @param field name of the field, for the error message
     * @return the number entered
     * @throws ValidationException if the text is not a whole number
     */
    private static int parseNumber(String input, String field) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new ValidationException(field + " must be a whole number.");
        }
    }

    /**
     * Parses an ISO date typed by the operator.
     *
     * @param input text such as {@code 2026-11-02}
     * @return the date
     * @throws ValidationException if the text is not a valid date
     */
    private static LocalDate parseDate(String input) {
        try {
            return LocalDate.parse(input);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Date must be a real date written YYYY-MM-DD, such as 2026-11-02.");
        }
    }

    /**
     * Parses a 24-hour time typed by the operator.
     *
     * @param input text such as {@code 09:30}
     * @return the time
     * @throws ValidationException if the text is not a valid time
     */
    private static LocalTime parseTime(String input) {
        try {
            return LocalTime.parse(input);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Time must be written HH:MM in 24-hour time, such as 09:30.");
        }
    }

    /**
     * Splits a comma-separated list of IDs, ignoring empty entries. IDs are trimmed later by the domain.
     *
     * @param input text such as {@code "S001, S002"}
     * @return the IDs in the order typed
     */
    private static List<String> parseIds(String input) {
        return Arrays.stream(input.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Parses a comma-separated list of feature names, ignoring case and empty entries.
     *
     * @param input text such as {@code "whiteboard, projector"}; blank means no features
     * @return the features named
     * @throws ValidationException if a name is not a {@link RoomFeature}
     */
    private static Set<RoomFeature> parseFeatures(String input) {
        Set<RoomFeature> features = EnumSet.noneOf(RoomFeature.class);
        for (String part : input.split(",")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            try {
                features.add(RoomFeature.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Unknown room feature: " + name + ".");
            }
        }
        return features;
    }

    /**
     * Prints rooms as an aligned table, or a message if there are none.
     *
     * @param list rooms to print
     */
    private void printRooms(List<Room> list) {
        if (list.isEmpty()) {
            out.println("No rooms match.");
            return;
        }
        out.printf("%-5s %-30s %8s  %s%n", "ID", "Name", "Capacity", "Features");
        for (Room room : list) {
            out.printf("%-5s %-30s %8d  %s%n", room.id(), room.name(), room.capacity(),
                    formatFeatures(room.features()));
        }
    }

    /**
     * Prints one line per student, or a message if there are none.
     *
     * @param list students to print
     */
    private void printStudents(List<Student> list) {
        if (list.isEmpty()) {
            out.println("No students found.");
            return;
        }
        list.forEach(s -> out.println(format(s)));
    }

    /**
     * Prints one line per availability interval, or a message if there are none.
     *
     * @param list merged intervals to print
     */
    private void printAvailability(List<AvailabilityInterval> list) {
        if (list.isEmpty()) {
            out.println("  No availability entered.");
            return;
        }
        list.forEach(i -> out.println("  " + i.date() + "  " + i.start() + "-" + i.end()));
    }

    /**
     * Formats a student as {@code ID  Name <email>}.
     *
     * @param s the student
     * @return the one-line summary
     */
    private static String format(Student s) {
        return s.id() + "  " + s.name() + " <" + s.email() + ">";
    }

    /**
     * Formats an event as {@code ID  STATUS  first to last  Title}.
     *
     * @param e the event
     * @return the one-line summary
     */
    private static String format(Event e) {
        return e.id() + "  " + e.status() + "  " + e.details().firstDay() + " to " + e.details().lastDay()
                + "  " + e.details().title();
    }

    /**
     * Formats features as a comma-separated list, or a dash if there are none.
     *
     * @param features the features
     * @return the display text
     */
    private static String formatFeatures(Set<RoomFeature> features) {
        return features.isEmpty() ? "-" : features.stream().map(Enum::name).collect(Collectors.joining(", "));
    }

    /**
     * Prompts for a line, treating end of input as an empty answer.
     *
     * @param label prompt text, without the trailing colon
     * @return the trimmed answer, or an empty string at end of input
     */
    private String promptOrEmpty(String label) {
        String line = prompt(label);
        return line == null ? "" : line;
    }

    /**
     * Prints a prompt and reads one line of input.
     *
     * @param label prompt text, without the trailing colon
     * @return the trimmed answer, or null at end of input
     * @throws UncheckedIOException if reading input fails
     */
    private String prompt(String label) {
        out.print(label + ": ");
        out.flush();
        try {
            String line = in.readLine();
            if (line == null) {
                return null;
            }
            // Input piped from a UTF-8 file or PowerShell can start with a byte-order mark; it is not operator text.
            return line.replace("﻿", "").trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
