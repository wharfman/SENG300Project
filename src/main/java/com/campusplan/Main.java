/*
 * Main.java
 *
 * Application entry point: opens the data file, wires the layers together,
 * and starts the command-line interface.
 */
package com.campusplan;

import com.campusplan.application.AvailabilityService;
import com.campusplan.application.EventService;
import com.campusplan.application.RoomService;
import com.campusplan.application.SeedLoader;
import com.campusplan.application.StudentService;
import com.campusplan.domain.Room;
import com.campusplan.persistence.JsonDataStore;
import com.campusplan.persistence.JsonRoomRepository;
import com.campusplan.persistence.StorageException;
import com.campusplan.ui.CampusPlanCli;
import com.campusplan.ui.CorruptDataPrompt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Starts CampusPlan. Not instantiable. */
public final class Main {

    /** Classpath location of the built-in room catalogue, used when there is no data file yet. */
    private static final String ROOM_CATALOGUE = "/seed/rooms.json";

    /** Sample datasets offered when the application starts empty, by description, in the order offered. */
    private static final Map<String, String> SAMPLES = new LinkedHashMap<>();

    static {
        SAMPLES.put("Sample data where a meeting can be scheduled", "/seed/campusplan-sample.json");
        SAMPLES.put("Sample data where no meeting can be scheduled", "/seed/campusplan-no-suggestion.json");
    }

    /** Data file used when no path is given on the command line. */
    private static final Path DEFAULT_DATA_FILE = Path.of("data", "campusplan.json");

    /** Prevents instantiation. */
    private Main() {
    }

    /**
     * Opens the data file and runs the CLI until the operator quits.
     * Exits with status 1 if the room catalogue cannot be loaded, or if the data file cannot be
     * loaded and the operator does not set it aside; the data file is then left unchanged.
     *
     * @param args optional path of the data file; defaults to {@code data/campusplan.json}
     */
    public static void main(String[] args) {
        Path dataFile = args.length > 0 ? Path.of(args[0]) : DEFAULT_DATA_FILE;
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        JsonDataStore store;
        try {
            List<Room> catalogue = JsonRoomRepository.fromResource(ROOM_CATALOGUE).findAll();
            store = openOrRecover(dataFile, catalogue, in);
        } catch (StorageException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return; // unreachable, but lets the compiler see that store is assigned below
        }
        if (store == null) {
            System.exit(1);
            return;
        }
        System.out.println("Data file: " + dataFile.toAbsolutePath());

        StudentService students = new StudentService(store.students());
        new CampusPlanCli(students,
                new RoomService(store.rooms()),
                new AvailabilityService(store.availability(), students),
                new EventService(store.events(), students),
                seedLoader(store), in, System.out).run();
    }

    /**
     * Opens the data file. If it cannot be loaded, asks the operator whether to set it aside and start empty.
     *
     * @param dataFile  the data file
     * @param catalogue rooms to use when there is no data file
     * @param in        operator input
     * @return the opened store, or null if the operator chose to quit
     * @throws StorageException if the file cannot be set aside
     */
    private static JsonDataStore openOrRecover(Path dataFile, List<Room> catalogue, BufferedReader in) {
        try {
            return JsonDataStore.open(dataFile, catalogue);
        } catch (StorageException e) {
            if (!new CorruptDataPrompt(in, System.out).askToSetAside(e.getMessage(), dataFile)) {
                return null;
            }
            Path aside = JsonDataStore.setAside(dataFile);
            System.out.println("Moved the old data file to " + aside.toAbsolutePath());
            return JsonDataStore.open(dataFile, catalogue);
        }
    }

    /**
     * Offers the bundled sample datasets while the store is empty.
     *
     * @param store the opened data store
     * @return a loader over {@link #SAMPLES}
     */
    private static SeedLoader seedLoader(JsonDataStore store) {
        return new SeedLoader() {
            @Override
            public boolean canLoad() {
                return store.isEmpty();
            }

            @Override
            public List<String> datasets() {
                return List.copyOf(SAMPLES.keySet());
            }

            @Override
            public void load(String dataset) {
                String resource = SAMPLES.get(dataset);
                try (InputStream seed = resource == null ? null : Main.class.getResourceAsStream(resource)) {
                    if (seed == null) {
                        throw new StorageException("Sample data not found: " + dataset);
                    }
                    store.loadSeed(seed);
                } catch (IOException e) {
                    throw new StorageException("Could not read sample data: " + e.getMessage(), e);
                }
            }
        };
    }
}
