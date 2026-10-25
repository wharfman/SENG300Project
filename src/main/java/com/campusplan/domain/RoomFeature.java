/*
 * RoomFeature.java
 *
 * Domain enumeration of the equipment and accessibility features a room can offer.
 */
package com.campusplan.domain;

/** A feature that a room may provide and an event may require. */
public enum RoomFeature {
    /** The room has a whiteboard. */
    WHITEBOARD,
    /** The room has a projector. */
    PROJECTOR,
    /** The room is wheelchair accessible. */
    ACCESSIBLE
}
