package com.campusplan.ui;

import com.campusplan.application.StudentService;
import com.campusplan.domain.Student;
import com.campusplan.domain.ValidationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.List;

/** Menu-driven text interface. Reaching end of input quits. */
public class CampusPlanCli {
    private static final String MENU = """

            CampusPlan
              1. Add student
              2. Edit student
              3. View student
              4. Search students
              0. Quit""";

    private final StudentService students;
    private final BufferedReader in;
    private final PrintStream out;

    public CampusPlanCli(StudentService students, BufferedReader in, PrintStream out) {
        this.students = students;
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
