/*
 * JsonDataStoreTest.java
 *
 * Integration tests for JsonDataStore: real JSON round trips in temporary folders,
 * missing and corrupt files, failed saves, and seed loading.
 */
package com.campusplan.persistence;

import com.campusplan.application.AvailabilityService;
import com.campusplan.application.EventService;
import com.campusplan.application.StudentService;
import com.campusplan.domain.Availability;
import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.EventDetails;
import com.campusplan.domain.Room;
import com.campusplan.domain.RoomFeature;
import com.campusplan.domain.Student;
import com.campusplan.domain.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Saving, reloading, and failure handling of {@link JsonDataStore} against real files. */
class JsonDataStoreTest {

    /** First day used for availability and events. */
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    /** A small room catalogue standing in for the seed catalogue. */
    private static final List<Room> CATALOGUE = List.of(
            new Room("R01", "Maple Hall 101", 6, Set.of(RoomFeature.WHITEBOARD)),
            new Room("R02", "Birch Study Room A", 4, Set.of()));

    /** Fresh temporary folder for each test, deleted afterwards. */
    @TempDir
    Path dir;

    /** The data file inside {@link #dir}. */
    private Path file() {
        return dir.resolve("campusplan.json");
    }

    /** Wraps JSON text as a UTF-8 stream. */
    private static InputStream json(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Writes the given text to the data file. */
    private void writeFile(String text) throws IOException {
        Files.writeString(file(), text, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Given no data file, when the store is opened, then it starts empty with the catalogue rooms and creates no file")
    void missingFileStartsEmpty() {
        JsonDataStore store = JsonDataStore.open(file(), CATALOGUE);

        assertTrue(store.isEmpty());
        assertEquals(CATALOGUE, store.rooms().findAll());
        assertFalse(Files.exists(file()));
    }

    @Test
    @DisplayName("Given students, availability, and a draft event, when the store is reopened, then everything is restored with the same IDs")
    void roundTripsAllData() {
        JsonDataStore store = JsonDataStore.open(file(), CATALOGUE);
        StudentService students = new StudentService(store.students());
        Student ada = students.create("Ada", "ada@example.com");
        Student grace = students.create("Grace", "grace@example.com");
        new AvailabilityService(store.availability(), students)
                .add(ada.id(), DAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        Event event = new EventService(store.events(), students).create(new EventDetails(
                "Project meeting", List.of(ada.id(), grace.id()), DAY, DAY.plusDays(2), 120,
                Set.of(RoomFeature.WHITEBOARD, RoomFeature.PROJECTOR), 2));

        JsonDataStore reopened = JsonDataStore.open(file(), List.of());

        assertEquals(List.of(ada, grace), reopened.students().findAll());
        assertEquals(store.availability().findAll(), reopened.availability().findAll());
        assertEquals(List.of(event), reopened.events().findAll());
        assertEquals(CATALOGUE, reopened.rooms().findAll());
    }

    @Test
    @DisplayName("Given saved students, when the store is reopened and a student is added, then the next ID follows the saved ones")
    void preservesIdSequenceAfterRestart() {
        StudentService before = new StudentService(JsonDataStore.open(file(), CATALOGUE).students());
        before.create("Ada", "ada@example.com");
        before.create("Grace", "grace@example.com");

        StudentService after = new StudentService(JsonDataStore.open(file(), CATALOGUE).students());

        assertEquals("S003", after.create("Alan", "alan@example.com").id());
    }

    @Test
    @DisplayName("Given a name with accented and non-Latin characters, when saved, then the file is UTF-8 and the name reloads unchanged")
    void savesUtf8() throws IOException {
        new StudentService(JsonDataStore.open(file(), CATALOGUE).students()).create("Zoë Ñúñez 李", "zoe@example.com");

        assertTrue(Files.readString(file(), StandardCharsets.UTF_8).contains("Zoë Ñúñez 李"));
        assertEquals("Zoë Ñúñez 李", JsonDataStore.open(file(), CATALOGUE).students().findAll().get(0).name());
    }

    @Test
    @DisplayName("Given saved availability that overlaps or touches, when the store is opened, then each date shows one merged interval")
    void mergesSavedAvailabilityOnLoad() throws IOException {
        writeFile("""
                {"formatVersion":1,"rooms":[],"events":[],
                 "students":[{"id":"S001","name":"Ada","email":"ada@example.com"}],
                 "availability":[{"studentId":"S001","intervals":[
                     {"date":"2026-11-02","start":"13:00","end":"15:00"},
                     {"date":"2026-11-02","start":"14:00","end":"16:00"},
                     {"date":"2026-11-02","start":"16:00","end":"17:00"}]}]}""");

        Availability loaded = JsonDataStore.open(file(), CATALOGUE).availability().findByStudentId("S001").orElseThrow();

        assertEquals(List.of(new AvailabilityInterval(DAY, LocalTime.of(13, 0), LocalTime.of(17, 0))),
                loaded.intervals());
    }

    @Test
    @DisplayName("Given a malformed or empty data file, when the store is opened, then a clear error is raised and the file is not changed")
    void rejectsCorruptFileWithoutChangingIt() throws IOException {
        for (String text : new String[] {"{ \"students\": [", "", "null", "[]"}) {
            writeFile(text);
            byte[] original = Files.readAllBytes(file());

            StorageException e = assertThrows(StorageException.class, () -> JsonDataStore.open(file(), CATALOGUE));

            assertTrue(e.getMessage().contains("campusplan.json"), e.getMessage());
            assertArrayEquals(original, Files.readAllBytes(file()));
        }
    }

    @Test
    @DisplayName("Given a data file with an invalid student email, when the store is opened, then it is rejected as corrupt")
    void rejectsInvalidRecord() throws IOException {
        writeFile("""
                {"formatVersion":1,"rooms":[],"availability":[],"events":[],
                 "students":[{"id":"S001","name":"Ada","email":"not-an-email"}]}""");

        assertThrows(StorageException.class, () -> JsonDataStore.open(file(), CATALOGUE));
    }

    @Test
    @DisplayName("Given a data file with duplicate student IDs or emails, when the store is opened, then it is rejected as corrupt")
    void rejectsDuplicateStudents() throws IOException {
        writeFile("""
                {"formatVersion":1,"rooms":[],"availability":[],"events":[],
                 "students":[{"id":"S001","name":"Ada","email":"ada@example.com"},
                             {"id":"S001","name":"Grace","email":"grace@example.com"}]}""");
        assertThrows(StorageException.class, () -> JsonDataStore.open(file(), CATALOGUE));

        writeFile("""
                {"formatVersion":1,"rooms":[],"availability":[],"events":[],
                 "students":[{"id":"S001","name":"Ada","email":"ada@example.com"},
                             {"id":"S002","name":"Grace","email":"ADA@example.com"}]}""");
        assertThrows(StorageException.class, () -> JsonDataStore.open(file(), CATALOGUE));
    }

    @Test
    @DisplayName("Given a data file where an event or availability refers to an unknown student, when the store is opened, then it is rejected as corrupt")
    void rejectsDanglingReferences() throws IOException {
        writeFile("""
                {"formatVersion":1,"rooms":[],"events":[],
                 "students":[{"id":"S001","name":"Ada","email":"ada@example.com"}],
                 "availability":[{"studentId":"S999","intervals":[]}]}""");
        assertThrows(StorageException.class, () -> JsonDataStore.open(file(), CATALOGUE));

        writeFile("""
                {"formatVersion":1,"rooms":[],"availability":[],
                 "students":[{"id":"S001","name":"Ada","email":"ada@example.com"}],
                 "events":[{"id":"E001","title":"Meeting","inviteeIds":["S001","S999"],
                            "firstDay":"2026-11-02","lastDay":"2026-11-02","durationMinutes":60,
                            "requiredFeatures":[],"minimumAttendance":2,"status":"DRAFT",
                            "responses":{"S001":"PENDING","S999":"PENDING"}}]}""");
        assertThrows(StorageException.class, () -> JsonDataStore.open(file(), CATALOGUE));
    }

    @Test
    @DisplayName("Given a data file with a bad date or time, when the store is opened, then it is rejected as corrupt")
    void rejectsBadDateOrTime() throws IOException {
        writeFile("""
                {"formatVersion":1,"rooms":[],"events":[],
                 "students":[{"id":"S001","name":"Ada","email":"ada@example.com"}],
                 "availability":[{"studentId":"S001",
                                  "intervals":[{"date":"2026-13-40","start":"09:00","end":"10:00"}]}]}""");

        assertThrows(StorageException.class, () -> JsonDataStore.open(file(), CATALOGUE));
    }

    @Test
    @DisplayName("Given a data file with an unknown format version, when the store is opened, then it is rejected")
    void rejectsUnknownFormatVersion() throws IOException {
        writeFile("{\"formatVersion\":99,\"rooms\":[],\"students\":[],\"availability\":[],\"events\":[]}");

        StorageException e = assertThrows(StorageException.class, () -> JsonDataStore.open(file(), CATALOGUE));
        assertTrue(e.getMessage().contains("99"), e.getMessage());
    }

    @Test
    @DisplayName("Given a saved file, when a later save fails, then an error is raised, the previous file is kept, and nothing changes in memory")
    void failedSaveKeepsPreviousFile() throws IOException {
        JsonDataStore store = JsonDataStore.open(file(), CATALOGUE);
        StudentService students = new StudentService(store.students());
        Student ada = students.create("Ada", "ada@example.com");
        byte[] saved = Files.readAllBytes(file());
        // A directory where the temporary file should go makes the next write fail.
        Files.createDirectory(dir.resolve("campusplan.json.tmp"));

        assertThrows(StorageException.class, () -> students.create("Grace", "grace@example.com"));
        assertThrows(StorageException.class, () -> students.edit(ada.id(), "Ada L", "ada@example.com"));

        assertArrayEquals(saved, Files.readAllBytes(file()));
        assertEquals(List.of(ada), store.students().findAll());
        assertEquals(List.of(ada), JsonDataStore.open(file(), CATALOGUE).students().findAll());
    }

    @Test
    @DisplayName("Given a saved student, when the same student is saved again unchanged, then the file is not rewritten")
    void unchangedSaveDoesNotWrite() throws IOException {
        JsonDataStore store = JsonDataStore.open(file(), CATALOGUE);
        Student ada = new StudentService(store.students()).create("Ada", "ada@example.com");
        // Any write would now fail, so an exception would show the file was rewritten.
        Files.createDirectory(dir.resolve("campusplan.json.tmp"));

        store.students().save(ada);
        new StudentService(store.students()).edit(ada.id(), "Ada", "ADA@example.com");

        assertEquals(List.of(ada), store.students().findAll());
    }

    @Test
    @DisplayName("Given a data file cut off partway through, when the store is opened, then the error gives the line and column")
    void corruptFileErrorNamesLineAndColumn() throws IOException {
        writeFile("{\n  \"formatVersion\": 1,\n  \"students\": [ { \"id\": \"S001\", ");

        StorageException e = assertThrows(StorageException.class, () -> JsonDataStore.open(file(), CATALOGUE));

        assertTrue(e.getMessage().contains("at line 3, column"), e.getMessage());
    }

    @Test
    @DisplayName("Given a corrupt data file, when it is set aside, then it keeps identical contents under a new name and the store opens empty")
    void setAsideKeepsCorruptFile() throws IOException {
        writeFile("{ broken");
        byte[] original = Files.readAllBytes(file());

        Path aside = JsonDataStore.setAside(file());

        assertFalse(Files.exists(file()));
        assertTrue(aside.getFileName().toString().startsWith("campusplan.json.corrupt-"));
        assertArrayEquals(original, Files.readAllBytes(aside));
        assertTrue(JsonDataStore.open(file(), CATALOGUE).isEmpty());
    }

    @Test
    @DisplayName("Given a leftover temporary file from a save that was interrupted, when the store is opened, then the last complete save is loaded")
    void interruptedSaveLoadsLastCompleteSave() throws IOException {
        new StudentService(JsonDataStore.open(file(), CATALOGUE).students()).create("Ada", "ada@example.com");
        Files.writeString(dir.resolve("campusplan.json.tmp"), "{ half-written", StandardCharsets.UTF_8);

        JsonDataStore reopened = JsonDataStore.open(file(), CATALOGUE);

        assertEquals("Ada", reopened.students().findAll().get(0).name());
        new StudentService(reopened.students()).create("Grace", "grace@example.com");
        assertEquals(2, JsonDataStore.open(file(), CATALOGUE).students().findAll().size());
    }

    @Test
    @DisplayName("Given an empty store, when seed data is loaded, then it is stored and saved; loading again is rejected without change")
    void loadsSeedOnlyIntoEmptyStore() {
        String seed = """
                {"formatVersion":1,
                 "rooms":[{"id":"R09","name":"Seed Room","capacity":8,"features":["PROJECTOR"]}],
                 "students":[{"id":"S001","name":"Ada","email":"ada@example.com"},
                             {"id":"S002","name":"Grace","email":"grace@example.com"}],
                 "availability":[{"studentId":"S001",
                                  "intervals":[{"date":"2026-11-02","start":"09:00","end":"11:00"}]}],
                 "events":[]}""";
        JsonDataStore store = JsonDataStore.open(file(), CATALOGUE);

        store.loadSeed(json(seed));

        assertEquals(2, store.students().findAll().size());
        assertEquals("R09", store.rooms().findAll().get(0).id());
        assertEquals(2, JsonDataStore.open(file(), CATALOGUE).students().findAll().size());
        assertThrows(ValidationException.class, () -> store.loadSeed(json(seed)));
        assertEquals(2, store.students().findAll().size());
    }

    @Test
    @DisplayName("Given an empty store, when corrupt seed data is loaded, then it is rejected and the store stays empty")
    void rejectsCorruptSeed() {
        JsonDataStore store = JsonDataStore.open(file(), CATALOGUE);

        assertThrows(StorageException.class, () -> store.loadSeed(json("{ not json")));

        assertTrue(store.isEmpty());
        assertFalse(Files.exists(file()));
    }
}
