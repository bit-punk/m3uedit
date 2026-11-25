package org.bitpunk.m3uedit.api;

import java.io.IOException;

/**
 * Data a plugin collects while it is used and which the user may want to clear, e.g. recently used servers or
 * downloaded files. m3uedit shows it in its configuration dialog, with its size, and clears it on request.
 * <p>
 * Plugins offer it by {@link Plugin#getCollectedData()}; data shared by several plugins (e.g. by an input and an output)
 * is shown once, identified by its {@link #getId()}.
 */
public interface CollectedData {

    /**
     * @return identifies the data among the data of all plugins, e.g. {@code de.example.recentServers}
     */
    String getId();

    /**
     * @return the name shown to the user, in the language of the user interface, e.g. "SFTP servers"
     */
    String getName();

    /**
     * @return how much has been collected, e.g. "3 entries" or "45.8 MB in 2 files"
     */
    String describe();

    /**
     * @return a short hint shown below the data, e.g. what clearing means; null if there is none
     */
    default String getHint() {
        return null;
    }

    /**
     * @return true if nothing has been collected (it cannot be cleared then)
     */
    boolean isEmpty();

    /**
     * Deletes the collected data.
     *
     * @throws IOException if it could not be deleted completely; the message is shown to the user
     */
    void clear() throws IOException;
}
