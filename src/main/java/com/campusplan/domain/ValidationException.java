/*
 * ValidationException.java
 *
 * Domain-layer exception signalling that operator input breaks a CampusPlan rule.
 */
package com.campusplan.domain;

/**
 * Thrown when input breaks a domain rule, such as an invalid name or a duplicate email.
 * The message is written for the operator and is shown as-is by the user interface.
 */
public class ValidationException extends RuntimeException {

    /**
     * Creates an exception with an operator-facing message.
     *
     * @param message explanation of which rule was broken
     */
    public ValidationException(String message) {
        super(message);
    }
}
