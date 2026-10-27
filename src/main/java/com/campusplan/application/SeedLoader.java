/*
 * SeedLoader.java
 *
 * Application-layer abstraction for loading sample data into an empty application,
 * so the user interface can offer it without knowing how data is stored.
 */
package com.campusplan.application;

/** Loads the documented seed data. Seed loading only happens into an empty application. */
public interface SeedLoader {

    /** A loader that never offers seed data, for sessions that start with data or in tests. */
    SeedLoader NONE = new SeedLoader() {
        @Override
        public boolean canLoad() {
            return false;
        }

        @Override
        public void load() {
            throw new IllegalStateException("No seed data is available.");
        }
    };

    /**
     * Whether seed data can be loaded now.
     *
     * @return true if the application is empty and seed data is available
     */
    boolean canLoad();

    /**
     * Loads the seed data and saves it.
     *
     * @throws com.campusplan.domain.ValidationException     if the application is not empty
     * @throws com.campusplan.persistence.StorageException if the seed data is corrupt or cannot be saved
     */
    void load();
}
