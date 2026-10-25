package com.campusplan;

import com.campusplan.application.RoomService;
import com.campusplan.application.StudentService;
import com.campusplan.persistence.InMemoryStudentRepository;
import com.campusplan.persistence.JsonRoomRepository;
import com.campusplan.persistence.StorageException;
import com.campusplan.ui.CampusPlanCli;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class Main {
    private static final String ROOM_CATALOGUE = "/seed/rooms.json";

    private Main() {
    }

    public static void main(String[] args) {
        RoomService rooms;
        try {
            rooms = new RoomService(JsonRoomRepository.fromResource(ROOM_CATALOGUE));
        } catch (StorageException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        }

        StudentService students = new StudentService(new InMemoryStudentRepository());
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        new CampusPlanCli(students, rooms, in, System.out).run();
    }
}
