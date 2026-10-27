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
import com.campusplan.persistence.JsonDataStore;
import com.campusplan.persistence.JsonRoomRepository;
import com.campusplan.persistence.StorageException;
import com.campusplan.ui.CampusPlanCli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Starts CampusPlan. Not instantiable. */
public final class Main {

    /** Classpath location of the built-in room catalogue, used when there is no data file yet. */
    private static final String ROOM_CATALOGUE = "/seed/rooms.json";

    /** Classpath location of the sample data offered when the application starts empty. */
    private static final String SEED_DATA = "/seed/campusplan-seed.json";

    /** Data file used when no path is given on the command line. */
    private static final Path DEFAULT_DATA_FILE = Path.of("data", "campusplan.json");

    /** Prevents instantiation. */
    private Main() {
    }

    /**
     * Opens the data file and runs the CLI until the operator quits.
     * Exits with status 1 if the room catalogue or data file cannot be loaded; the data file is left unchanged.
     *
     * @param args optional path of the data file; defaults to {@code data/campusplan.json}
     */
    public static void main(String[] args) {
        Path dataFile = args.length > 0 ? Path.of(args[0]) : DEFAULT_DATA_FILE;
        JsonDataStore store;
        try {
            store = JsonDataStore.open(dataFile, JsonRoomRepository.fromResource(ROOM_CATALOGUE).findAll());
        } catch (StorageException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return; // unreachable, but lets the compiler see that store is assigned below
        }
        System.out.println("Data file: " + dataFile.toAbsolutePath());

        StudentService students = new StudentService(store.students());
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        new CampusPlanCli(students,
                new RoomService(store.rooms()),
                new AvailabilityService(store.availability(), students),
                new EventService(store.events(), students),
                seedLoader(store), in, System.out).run();
    }

    /**
     * Offers the bundled sample data while the store is empty.
     *
     * @param store the opened data store
     * @return a loader over {@link #SEED_DATA}
     */
    private static SeedLoader seedLoader(JsonDataStore store) {
        return new SeedLoader() {
            @Override
            public boolean canLoad() {
                return store.isEmpty() && Main.class.getResource(SEED_DATA) != null;
            }

            @Override
            public void load() {
                try (InputStream seed = Main.class.getResourceAsStream(SEED_DATA)) {
                    if (seed == null) {
                        throw new StorageException("Sample data not found: " + SEED_DATA);
                    }
                    store.loadSeed(seed);
                } catch (IOException e) {
                    throw new StorageException("Could not read sample data: " + e.getMessage(), e);
                }
            }
        };
    }
}
