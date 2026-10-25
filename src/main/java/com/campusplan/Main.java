/*
 * Main.java
 *
 * Application entry point: loads the room catalogue, wires the layers together,
 * and starts the command-line interface.
 */
package com.campusplan;

import com.campusplan.application.RoomService;
import com.campusplan.application.StudentService;
import com.campusplan.persistence.InMemoryStudentRepository;
import com.campusplan.persistence.JsonRoomRepository;
import com.campusplan.persistence.StorageException;
import com.campusplan.ui.CampusPlanCli;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Starts CampusPlan. Not instantiable. */
public final class Main {

    /** Classpath location of the seed room catalogue. */
    private static final String ROOM_CATALOGUE = "/seed/rooms.json";

    /** Prevents instantiation. */
    private Main() {
    }

    /**
     * Loads the room catalogue and runs the CLI until the operator quits.
     * Exits with status 1 if the catalogue cannot be loaded.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        RoomService rooms;
        try {
            rooms = new RoomService(JsonRoomRepository.fromResource(ROOM_CATALOGUE));
        } catch (StorageException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return; // unreachable, but lets the compiler see that rooms is assigned below
        }

        StudentService students = new StudentService(new InMemoryStudentRepository());
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        new CampusPlanCli(students, rooms, in, System.out).run();
    }
}
