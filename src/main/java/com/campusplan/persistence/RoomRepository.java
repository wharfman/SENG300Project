/*
 * RoomRepository.java
 *
 * Persistence-layer abstraction for reading the room catalogue, so the
 * application layer does not depend on where rooms come from.
 */
package com.campusplan.persistence;

import com.campusplan.domain.Room;

import java.util.List;

/** Read-only access to the room catalogue. */
public interface RoomRepository {

    /**
     * Returns every room in the catalogue.
     *
     * @return an unmodifiable list of all rooms, in catalogue order
     */
    List<Room> findAll();
}
