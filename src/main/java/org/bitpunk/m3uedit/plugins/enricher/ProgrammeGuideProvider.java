package org.bitpunk.m3uedit.plugins.enricher;

import org.bitpunk.m3uedit.model.Entry;

import java.io.IOException;
import java.util.List;

/**
 * An enricher that can provide the programmes of the channels of a playlist, e.g. to show them to the user.
 */
public interface ProgrammeGuideProvider {

    /**
     * Reads the programmes of the channels of the playlist. Blocks (it may read large files or the network),
     * so it must not be called on the JavaFX application thread.
     *
     * @param playlist the playlist, its channels are identified by their tvg-id
     * @return the programmes, sorted by their start
     */
    List<Programme> readProgrammes(List<Entry> playlist) throws IOException;
}
