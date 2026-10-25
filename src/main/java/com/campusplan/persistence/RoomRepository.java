package com.campusplan.persistence;

import com.campusplan.domain.Room;

import java.util.List;

public interface RoomRepository {
    List<Room> findAll();
}
