/*
 * JsonDataStore.java
 *
 * Persistence-layer file storage: keeps rooms, students, availability, and events in
 * one UTF-8 JSON file and exposes them through the repository interfaces.
 */
package com.campusplan.persistence;

import com.campusplan.domain.AttendanceResponse;
import com.campusplan.domain.Availability;
import com.campusplan.domain.AvailabilityInterval;
import com.campusplan.domain.Event;
import com.campusplan.domain.EventDetails;
import com.campusplan.domain.EventStatus;
import com.campusplan.domain.Room;
import com.campusplan.domain.RoomFeature;
import com.campusplan.domain.Student;
import com.campusplan.domain.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * All application data in one UTF-8 JSON file. Every change is written straight to disk.
 *
 * <ul>
 *   <li>A missing file means an empty application: no students, availability, or events,
 *       and rooms from the built-in catalogue. No file is created until the first save.</li>
 *   <li>A file that cannot be parsed, has an unknown format version, or breaks a rule
 *       (invalid record, duplicate ID, reference to an unknown student) is reported as a
 *       {@link StorageException} and is never overwritten. The operator may {@link #setAside set it aside}.</li>
 *   <li>A save writes a temporary file and then moves it over the old one, so a failed save
 *       leaves the previous file intact. The change is also undone in memory and a
 *       {@link StorageException} is thrown.</li>
 * </ul>
 */
public class JsonDataStore {

    /** Version written to, and required in, every data file. */
    public static final int FORMAT_VERSION = 1;

    /** Shared Jackson mapper; thread-safe once configured. */
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** The data file. */
    private final Path file;

    /** The room catalogue. */
    private List<Room> rooms;

    /** Students keyed by ID, in insertion order. */
    private final Map<String, Student> students = new LinkedHashMap<>();

    /** Availability keyed by student ID, in insertion order. */
    private final Map<String, Availability> availability = new LinkedHashMap<>();

    /** Events keyed by ID, in insertion order. */
    private final Map<String, Event> events = new LinkedHashMap<>();

    /**
     * Creates a store over already-validated data.
     *
     * @param file the data file
     * @param data the contents to start with
     */
    private JsonDataStore(Path file, Contents data) {
        this.file = file;
        replaceWith(data);
    }

    /**
     * Opens the data file, or starts empty if it does not exist.
     *
     * @param file      path of the data file
     * @param catalogue rooms to use when the file does not exist yet
     * @return the opened store
     * @throws StorageException if the file exists but cannot be read or is corrupt; the file is left unchanged
     */
    public static JsonDataStore open(Path file, List<Room> catalogue) {
        if (!Files.exists(file)) {
            return new JsonDataStore(file, new Contents(catalogue, List.of(), List.of(), List.of()));
        }
        try (InputStream in = Files.newInputStream(file)) {
            return new JsonDataStore(file, read(in, "Data file " + file));
        } catch (IOException e) {
            throw new StorageException("Could not read data file " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Renames an unusable data file so the application can start empty, keeping its contents
     * for later repair. The new name adds {@code .corrupt-<timestamp>}, so earlier set-aside files are never replaced.
     *
     * @param file the data file to set aside
     * @return where the file now is
     * @throws StorageException if the file cannot be renamed; it is then left where it was
     */
    public static Path setAside(Path file) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path target = file.resolveSibling(file.getFileName() + ".corrupt-" + stamp);
        for (int n = 2; Files.exists(target); n++) {
            target = file.resolveSibling(file.getFileName() + ".corrupt-" + stamp + "-" + n);
        }
        try {
            return Files.move(file, target);
        } catch (IOException e) {
            throw new StorageException("Could not set aside " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Whether there are no students, availability, or events. Rooms do not count.
     *
     * @return true if the application has no data of its own yet
     */
    public boolean isEmpty() {
        return students.isEmpty() && availability.isEmpty() && events.isEmpty();
    }

    /**
     * Replaces the contents of an empty store with seed data and saves it.
     *
     * @param seed UTF-8 JSON in the data file format; the stream is not closed
     * @throws ValidationException if the store is not empty
     * @throws StorageException    if the seed data is corrupt or cannot be saved; the store is unchanged
     */
    public void loadSeed(InputStream seed) {
        if (!isEmpty()) {
            throw new ValidationException("Seed data can only be loaded into an empty application.");
        }
        Contents loaded = read(seed, "Seed data");
        Contents previous = snapshot();
        replaceWith(loaded);
        try {
            persist();
        } catch (StorageException e) {
            replaceWith(previous);
            throw e;
        }
    }

    /** @return the room catalogue */
    public RoomRepository rooms() {
        return () -> rooms;
    }

    /** @return student storage backed by this file */
    public StudentRepository students() {
        return new StudentRepository() {
            @Override
            public List<Student> findAll() {
                return List.copyOf(students.values());
            }

            @Override
            public Optional<Student> findById(String id) {
                return Optional.ofNullable(students.get(id));
            }

            @Override
            public void save(Student student) {
                saveInto(students, student.id(), student);
            }
        };
    }

    /** @return availability storage backed by this file */
    public AvailabilityRepository availability() {
        return new AvailabilityRepository() {
            @Override
            public List<Availability> findAll() {
                return List.copyOf(availability.values());
            }

            @Override
            public Optional<Availability> findByStudentId(String studentId) {
                return Optional.ofNullable(availability.get(studentId));
            }

            @Override
            public void save(Availability entry) {
                saveInto(availability, entry.studentId(), entry);
            }
        };
    }

    /** @return event storage backed by this file */
    public EventRepository events() {
        return new EventRepository() {
            @Override
            public List<Event> findAll() {
                return List.copyOf(events.values());
            }

            @Override
            public Optional<Event> findById(String id) {
                return Optional.ofNullable(events.get(id));
            }

            @Override
            public void save(Event event) {
                saveInto(events, event.id(), event);
            }
        };
    }

    /**
     * Stores a value and writes the file; on failure the previous value is restored.
     *
     * @param map   the collection being changed
     * @param key   the record's key
     * @param value the new record
     * @param <T>   record type
     * @throws StorageException if the file cannot be written
     */
    private <T> void saveInto(Map<String, T> map, String key, T value) {
        if (value.equals(map.get(key))) {
            return; // Nothing changed, so the file is not rewritten.
        }
        T previous = map.put(key, value);
        try {
            persist();
        } catch (StorageException e) {
            if (previous == null) {
                map.remove(key);
            } else {
                map.put(key, previous);
            }
            throw e;
        }
    }

    /**
     * Writes all data to a temporary file, then moves it over the data file.
     *
     * @throws StorageException if writing or moving fails; the previous data file is kept
     */
    private void persist() {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            // writeValueAsBytes always encodes as UTF-8.
            Files.write(temp, MAPPER.writeValueAsBytes(toJson(snapshot())));
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            deleteTempQuietly(temp);
            throw new StorageException("Could not save data to " + file + ": " + e.getMessage()
                    + ". The previous saved data was kept.", e);
        }
    }

    /**
     * Removes a leftover temporary file, ignoring errors. Leaves anything that is not a regular file alone.
     *
     * @param temp the temporary file
     */
    private static void deleteTempQuietly(Path temp) {
        try {
            if (Files.isRegularFile(temp)) {
                Files.delete(temp);
            }
        } catch (IOException ignored) {
            // The next save overwrites it anyway.
        }
    }

    /** @return a copy of the current contents */
    private Contents snapshot() {
        return new Contents(rooms, List.copyOf(students.values()),
                List.copyOf(availability.values()), List.copyOf(events.values()));
    }

    /**
     * Replaces every collection with the given contents.
     *
     * @param data the new contents
     */
    private void replaceWith(Contents data) {
        rooms = List.copyOf(data.rooms());
        students.clear();
        data.students().forEach(s -> students.put(s.id(), s));
        availability.clear();
        data.availability().forEach(a -> availability.put(a.studentId(), a));
        events.clear();
        data.events().forEach(e -> events.put(e.id(), e));
    }

    /**
     * Parses and fully validates data in the file format.
     *
     * @param in     UTF-8 JSON; not closed
     * @param source what is being read, for error messages, such as "Data file campusplan.json"
     * @return the validated contents
     * @throws StorageException if the JSON is malformed, the version is unknown, or a rule is broken
     */
    private static Contents read(InputStream in, String source) {
        DataFileJson json;
        try {
            json = MAPPER.readValue(in, DataFileJson.class);
        } catch (JsonProcessingException e) {
            // Also covers a ValidationException thrown by a record constructor during parsing.
            String where = e.getLocation() == null ? ""
                    : " at line " + e.getLocation().getLineNr() + ", column " + e.getLocation().getColumnNr();
            throw new StorageException(source + " is corrupt" + where + ": " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new StorageException("Could not read " + source + ": " + e.getMessage(), e);
        }
        if (json == null) {
            throw new StorageException(source + " is corrupt: expected a JSON object.");
        }
        if (json.formatVersion() != FORMAT_VERSION) {
            throw new StorageException(source + " has unsupported format version " + json.formatVersion()
                    + "; expected " + FORMAT_VERSION + ".");
        }
        try {
            Contents data = fromJson(json);
            checkConsistent(data);
            return data;
        } catch (ValidationException | DateTimeParseException | StorageException e) {
            throw new StorageException(source + " is corrupt: " + e.getMessage(), e);
        }
    }

    /**
     * Checks rules that span records: unique IDs and emails, and references to existing students.
     *
     * @param data the parsed contents
     * @throws StorageException naming the first problem found
     */
    private static void checkConsistent(Contents data) {
        JsonRoomRepository.checkUnique(data.rooms());
        Set<String> studentIds = unique(data.students(), Student::id, "student ID");
        unique(data.students(), Student::email, "student email");
        unique(data.availability(), Availability::studentId, "availability entry for student");
        unique(data.events(), Event::id, "event ID");
        for (Availability entry : data.availability()) {
            requireStudent(studentIds, entry.studentId(), "Availability");
        }
        for (Event event : data.events()) {
            for (String invitee : event.details().inviteeIds()) {
                requireStudent(studentIds, invitee, "Event " + event.id());
            }
        }
    }

    /**
     * Collects keys, rejecting the first one that repeats.
     *
     * @param records records to check
     * @param key     extracts the key that must be unique
     * @param what    name of the key for the error message
     * @param <T>     record type
     * @return the set of keys
     * @throws StorageException if a key repeats
     */
    private static <T> Set<String> unique(List<T> records, Function<T, String> key, String what) {
        Set<String> seen = new HashSet<>();
        for (T record : records) {
            if (!seen.add(key.apply(record))) {
                throw new StorageException("duplicate " + what + " " + key.apply(record) + ".");
            }
        }
        return seen;
    }

    /**
     * Rejects a reference to a student that is not in the file.
     *
     * @param studentIds IDs of every stored student
     * @param id         the referenced ID
     * @param owner      what holds the reference, for the error message
     * @throws StorageException if the student does not exist
     */
    private static void requireStudent(Set<String> studentIds, String id, String owner) {
        if (!studentIds.contains(id)) {
            throw new StorageException(owner + " refers to unknown student " + id + ".");
        }
    }

    /**
     * Converts the file format into domain records, validating each one.
     *
     * @param json the parsed file
     * @return the domain contents
     * @throws ValidationException    if a record breaks a domain rule
     * @throws DateTimeParseException if a date or time is malformed
     */
    private static Contents fromJson(DataFileJson json) {
        List<Availability> availability = new ArrayList<>();
        for (AvailabilityJson entry : orEmpty(json.availability())) {
            List<AvailabilityInterval> intervals = orEmpty(entry.intervals()).stream()
                    .map(i -> new AvailabilityInterval(LocalDate.parse(i.date()),
                            LocalTime.parse(i.start()), LocalTime.parse(i.end())))
                    .toList();
            availability.add(new Availability(entry.studentId(), intervals));
        }
        List<Event> events = new ArrayList<>();
        for (EventJson e : orEmpty(json.events())) {
            EventDetails details = new EventDetails(e.title(), e.inviteeIds(),
                    LocalDate.parse(e.firstDay()), LocalDate.parse(e.lastDay()),
                    e.durationMinutes(), e.requiredFeatures(), e.minimumAttendance());
            events.add(new Event(e.id(), details, e.status(), e.responses() == null ? Map.of() : e.responses()));
        }
        return new Contents(orEmpty(json.rooms()), orEmpty(json.students()), availability, events);
    }

    /**
     * Converts domain records into the file format.
     *
     * @param data the contents to save
     * @return the file representation
     */
    private static DataFileJson toJson(Contents data) {
        List<AvailabilityJson> availability = data.availability().stream()
                .map(a -> new AvailabilityJson(a.studentId(), a.intervals().stream()
                        .map(i -> new IntervalJson(i.date().toString(), i.start().toString(), i.end().toString()))
                        .toList()))
                .toList();
        List<EventJson> events = data.events().stream()
                .map(e -> new EventJson(e.id(), e.details().title(), e.details().inviteeIds(),
                        e.details().firstDay().toString(), e.details().lastDay().toString(),
                        e.details().durationMinutes(), e.details().requiredFeatures(),
                        e.details().minimumAttendance(), e.status(), e.responses()))
                .toList();
        return new DataFileJson(FORMAT_VERSION, data.rooms(), data.students(), availability, events);
    }

    /**
     * Treats a missing JSON array as empty.
     *
     * @param list the parsed list, possibly null
     * @param <T>  element type
     * @return the list, or an empty list
     */
    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** Everything the store holds, as domain records. */
    private record Contents(List<Room> rooms, List<Student> students,
                            List<Availability> availability, List<Event> events) {
    }

    /** Top-level shape of the data file. Rooms and students are written as their domain records. */
    record DataFileJson(int formatVersion, List<Room> rooms, List<Student> students,
                        List<AvailabilityJson> availability, List<EventJson> events) {
    }

    /** One student's availability in the data file. */
    record AvailabilityJson(String studentId, List<IntervalJson> intervals) {
    }

    /** One availability interval in the data file, with ISO date and HH:mm times. */
    record IntervalJson(String date, String start, String end) {
    }

    /** One event in the data file, with ISO dates. */
    record EventJson(String id, String title, List<String> inviteeIds, String firstDay, String lastDay,
                     int durationMinutes, Set<RoomFeature> requiredFeatures, int minimumAttendance,
                     EventStatus status, Map<String, AttendanceResponse> responses) {
    }
}
