/*
 * SeedLoader.java
 *
 * Application-layer abstraction for loading sample data into an empty application,
 * so the user interface can offer it without knowing how data is stored.
 */
package com.campusplan.application;

import java.util.List;

/** Loads one of the documented sample datasets. Seed loading only happens into an empty application. */
public interface SeedLoader {

    /** A loader that never offers seed data, for sessions that start with data or in tests. */
    SeedLoader NONE = new SeedLoader() {
        @Override
        public boolean canLoad() {
            return false;
        }

        @Override
        public List<String> datasets() {
            return List.of();
        }

        @Override
        public void load(String dataset) {
            throw new IllegalStateException("No seed data is available.");
        }
    };

    /**
     * Whether seed data can be loaded now.
     *
     * @return true if the application is empty and at least one dataset is available
     */
    boolean canLoad();

    /**
     * Describes the datasets that can be loaded, in the order they should be offered.
     *
     * @return one operator-facing description per dataset
     */
    List<String> datasets();

    /**
     * Loads a dataset and saves it.
     *
     * @param dataset one of the descriptions returned by {@link #datasets()}
     * @throws com.campusplan.domain.ValidationException     if the application is not empty
     * @throws com.campusplan.persistence.StorageException if the data is corrupt or cannot be saved
     */
    void load(String dataset);
}
