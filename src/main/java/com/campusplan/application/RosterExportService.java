package com.campusplan.application;

import com.campusplan.domain.Event;
import com.campusplan.domain.Student;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Exports event rosters as UTF-8 CSV files.
 */
public class RosterExportService {

    private final EventService events;
    private final StudentService students;

    public RosterExportService(EventService events, StudentService students) {
        this.events = events;
        this.students = students;
    }

    public void exportRoster(String eventId, Path destination) throws IOException {
        Event event = events.get(eventId);
        String csv = buildCsv(event);

        Files.writeString(
            destination,
            csv,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        );
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"")
            || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }

    private String buildCsv(Event event) {
        StringBuilder csv = new StringBuilder();

        csv.append("student_id,name,email,response\r\n");

        for (String studentId : event.details().inviteeIds()) {
            Student student = students.get(studentId);

            csv.append(escapeCsv(student.id())).append(",");
            csv.append(escapeCsv(student.name())).append(",");
            csv.append(escapeCsv(student.email())).append(",");
            csv.append(escapeCsv(event.responses().get(studentId).name()));
            csv.append("\r\n");
        }

        return csv.toString();
    }
}