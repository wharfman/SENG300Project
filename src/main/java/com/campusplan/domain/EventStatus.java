/*
 * EventStatus.java
 *
 * Domain enumeration of the stages an event moves through.
 */
package com.campusplan.domain;

/** Where an event is in its life cycle. */
public enum EventStatus {
    /** Being planned; planning fields can still be edited. */
    DRAFT,
    /** Has a room and start/end time; planning fields are fixed. */
    SCHEDULED,
    /** No longer happening; the record and responses are kept. */
    CANCELLED
}
