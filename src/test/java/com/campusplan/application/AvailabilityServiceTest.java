/*
 * AvailabilityServiceTest.java
 *
 * Unit tests for AvailabilityService: adding, removing, and viewing a student's
 * availability, with unknown students and invalid intervals as error paths.
 */
package com.campusplan.application;

import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Student;
import com.campusplan.domain.ValidationException;
import com.campusplan.persistence.InMemoryAvailabilityRepository;
import com.campusplan.persistence.InMemoryStudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link AvailabilityService} operations over in-memory storage, including rejected operations. */
class AvailabilityServiceTest {

    /** A fixed date so tests only vary the times. */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    /** Service under test, recreated empty for each test. */
    private AvailabilityService service;

    /** A student who exists in every test. */
    private Student ada;

    /** Starts each test with one student and no availability. */
    @BeforeEach
    void setUp() {
        StudentService students = new StudentService(new InMemoryStudentRepository());
        ada = students.create("Ada", "ada@example.com");
        service = new AvailabilityService(new InMemoryAvailabilityRepository(), students);
    }

    /** Builds an interval on {@link #DAY} from "HH:mm" strings. */
    private static AvailabilityInterval on(String start, String end) {
        return new AvailabilityInterval(DAY, LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    @DisplayName("Given a student with no availability, when it is viewed, then the list is empty")
    void newStudentHasNoAvailability() {
        assertEquals(List.of(), service.get(ada.id()));
    }

    @Test
    @DisplayName("Given a student, when overlapping and adjacent intervals are added, then the stored availability is merged")
    void addMergesAndStores() {
        service.add(ada.id(), DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        service.add(ada.id(), DAY, LocalTime.of(10, 0), LocalTime.of(11, 0));
        List<AvailabilityInterval> result = service.add(ada.id(), DAY, LocalTime.of(10, 30), LocalTime.of(12, 0));

        assertEquals(List.of(on("09:00", "12:00")), result);
        assertEquals(result, service.get(ada.id()));
    }

    @Test
    @DisplayName("Given a student with availability, when part of it is removed, then the stored availability is updated")
    void removeUpdatesStoredAvailability() {
        service.add(ada.id(), DAY, LocalTime.of(9, 0), LocalTime.of(12, 0));

        service.remove(ada.id(), DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertEquals(List.of(on("10:00", "12:00")), service.get(ada.id()));
    }

    @Test
    @DisplayName("Given a padded student ID, when availability is added, then the student is found")
    void trimsStudentId() {
        service.add("  " + ada.id() + " ", DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertEquals(List.of(on("09:00", "10:00")), service.get(ada.id()));
    }

    @Test
    @DisplayName("Given no student with an ID, when availability is added, removed, or viewed, then it is rejected")
    void rejectsUnknownStudent() {
        assertThrows(ValidationException.class,
                () -> service.add("S999", DAY, LocalTime.of(9, 0), LocalTime.of(10, 0)));
        assertThrows(ValidationException.class,
                () -> service.remove("S999", DAY, LocalTime.of(9, 0), LocalTime.of(10, 0)));
        assertThrows(ValidationException.class, () -> service.get("S999"));
    }

    @Test
    @DisplayName("Given existing availability, when an invalid interval is added, then it is rejected and availability is unchanged")
    void rejectsInvalidIntervalWithoutChange() {
        service.add(ada.id(), DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertThrows(ValidationException.class,
                () -> service.add(ada.id(), DAY, LocalTime.of(21, 30), LocalTime.of(22, 30)));
        assertThrows(ValidationException.class,
                () -> service.add(ada.id(), DAY, LocalTime.of(11, 15), LocalTime.of(12, 0)));
        assertThrows(ValidationException.class,
                () -> service.add(ada.id(), DAY, LocalTime.of(12, 0), LocalTime.of(12, 0)));

        assertEquals(List.of(on("09:00", "10:00")), service.get(ada.id()));
    }

    @Test
    @DisplayName("Given existing availability, when time that is not available is removed, then it is rejected and availability is unchanged")
    void rejectsRemovingUnavailableTimeWithoutChange() {
        service.add(ada.id(), DAY, LocalTime.of(9, 0), LocalTime.of(10, 0));

        assertThrows(ValidationException.class,
                () -> service.remove(ada.id(), DAY, LocalTime.of(14, 0), LocalTime.of(15, 0)));

        assertEquals(List.of(on("09:00", "10:00")), service.get(ada.id()));
    }

    @Test
    @DisplayName("Given availability 10:00-14:00, when checking an event, then only a fully covered event counts as available")
    void isAvailableRequiresFullCoverage() {
        service.add(ada.id(), DAY, LocalTime.of(10, 0), LocalTime.of(14, 0));

        assertTrue(service.isAvailable(ada.id(), DAY, LocalTime.of(10, 0), LocalTime.of(14, 0)));
        assertFalse(service.isAvailable(ada.id(), DAY, LocalTime.of(13, 30), LocalTime.of(14, 30)));
    }
}
