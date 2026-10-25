package com.campusplan.domain;

/** Thrown when input breaks a domain rule. The message is shown to the operator. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
