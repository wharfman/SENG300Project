/*
 * StorageException.java
 *
 * Persistence-layer exception signalling that stored data is missing, unreadable, or invalid.
 */
package com.campusplan.persistence;

/** Thrown when stored data cannot be read or is invalid. */
public class StorageException extends RuntimeException {

    /**
     * Creates an exception describing a storage problem.
     *
     * @param message explanation shown to the operator
     */
    public StorageException(String message) {
        super(message);
    }

    /**
     * Creates an exception describing a storage problem caused by another exception.
     *
     * @param message explanation shown to the operator
     * @param cause   the underlying I/O or parsing error
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
