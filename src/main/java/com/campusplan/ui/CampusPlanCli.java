/*
 * CampusPlanCli.java
 *
 * User-interface layer: the interactive, menu-driven command-line interface through
 * which the organizer runs every CampusPlan workflow.
 */
package com.campusplan.ui;

import com.campusplan.application.RoomService;
import com.campusplan.application.StudentService;
import com.campusplan.domain.Room;
import com.campusplan.domain.RoomFeature;
import com.campusplan.domain.Student;
import com.campusplan.domain.ValidationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Menu-driven text interface. Reaching end of input quits.
 *
 * <p>Input and output are injected so the CLI can be driven by tests. Rule violations
 * are reported as {@code Error: ...} lines and the menu is shown again; they never end the session.
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
              0. Quit""";

    /** Student workflows. */
    private final StudentService students;
    /** Room catalogue queries. */
    private final RoomService rooms;
    /** Operator input, read one line per prompt. */
    private final BufferedReader in;
    /** Prompts and results. */
    private final PrintStream out;

    /**
     * Creates a CLI over the given services and streams.
     *
     * @param students student workflows
     * @param rooms    room catalogue queries
     * @param in       operator input
     * @param out      destination for prompts and results
     */
    public CampusPlanCli(StudentService students, RoomService rooms, BufferedReader in, PrintStream out) {
        this.students = students;
        this.rooms = rooms;
        this.in = in;
        this.out = out;
    }

    /** Shows the menu and handles choices until the operator quits or input ends. */
    public void run() {
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
                    default -> out.println("Unknown option: " + choice);
                }
            } catch (ValidationException e) {
                out.println("Error: " + e.getMessage());
            }
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
            String features = room.features().isEmpty() ? "-"
                    : room.features().stream().map(Enum::name).collect(Collectors.joining(", "));
            out.printf("%-5s %-30s %8d  %s%n", room.id(), room.name(), room.capacity(), features);
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
     * Formats a student as {@code ID  Name <email>}.
     *
     * @param s the student
     * @return the one-line summary
     */
    private static String format(Student s) {
        return s.id() + "  " + s.name() + " <" + s.email() + ">";
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
            return line == null ? null : line.trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
