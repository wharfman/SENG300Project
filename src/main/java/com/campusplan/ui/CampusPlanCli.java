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

/** Menu-driven text interface. Reaching end of input quits. */
public class CampusPlanCli {
    private static final String MENU = """

            CampusPlan
              1. Add student
              2. Edit student
              3. View student
              4. Search students
              5. List rooms
              6. Filter rooms
              0. Quit""";

    private final StudentService students;
    private final RoomService rooms;
    private final BufferedReader in;
    private final PrintStream out;

    public CampusPlanCli(StudentService students, RoomService rooms, BufferedReader in, PrintStream out) {
        this.students = students;
        this.rooms = rooms;
        this.in = in;
        this.out = out;
    }

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

    private void addStudent() {
        String name = promptOrEmpty("Name");
        String email = promptOrEmpty("Email");
        out.println("Created " + format(students.create(name, email)));
    }

    private void editStudent() {
        Student existing = students.get(promptOrEmpty("Student ID"));
        out.println("Editing " + format(existing) + " (leave blank to keep current value)");
        String name = promptOrEmpty("Name [" + existing.name() + "]");
        String email = promptOrEmpty("Email [" + existing.email() + "]");
        Student updated = students.edit(existing.id(),
                name.isBlank() ? existing.name() : name,
                email.isBlank() ? existing.email() : email);
        out.println("Updated " + format(updated));
    }

    private void viewStudent() {
        Student student = students.get(promptOrEmpty("Student ID"));
        out.println("ID:    " + student.id());
        out.println("Name:  " + student.name());
        out.println("Email: " + student.email());
    }

    private void filterRooms() {
        String capacityInput = promptOrEmpty("Minimum capacity (blank for any)");
        int minCapacity = capacityInput.isBlank() ? 1 : parseCapacity(capacityInput);
        String featureInput = promptOrEmpty("Required features, comma-separated "
                + List.of(RoomFeature.values()) + " (blank for none)");
        printRooms(rooms.filter(minCapacity, parseFeatures(featureInput)));
    }

    private static int parseCapacity(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new ValidationException("Minimum capacity must be a whole number.");
        }
    }

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

    private void printStudents(List<Student> list) {
        if (list.isEmpty()) {
            out.println("No students found.");
            return;
        }
        list.forEach(s -> out.println(format(s)));
    }

    private static String format(Student s) {
        return s.id() + "  " + s.name() + " <" + s.email() + ">";
    }

    private String promptOrEmpty(String label) {
        String line = prompt(label);
        return line == null ? "" : line;
    }

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
