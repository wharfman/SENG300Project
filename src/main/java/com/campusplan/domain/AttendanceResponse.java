/*
 * AttendanceResponse.java
 *
 * Domain enumeration of an invitee's reply to an event invitation.
 */
package com.campusplan.domain;

/** An invitee's reply, recorded by the organizer. Separate from whether the invitee is available. */
public enum AttendanceResponse {
    /** No reply yet; every invitee starts here. */
    PENDING,
    /** Will attend; reserves the invitee's time. */
    ACCEPTED,
    /** Will not attend. */
    DECLINED
}
