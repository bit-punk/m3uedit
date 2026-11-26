package org.bitpunk.m3uedit.plugins.missing;

import org.bitpunk.m3uedit.api.Enricher;
import org.bitpunk.m3uedit.api.Enrichment;
import org.bitpunk.m3uedit.api.Entry;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * An enricher of a project-file that is not installed: it is skipped, as if it was disabled.
 */
public class MissingEnricher extends MissingPlugin implements Enricher {

    public MissingEnricher(String typeId, ObjectNode settings) {
        super(typeId, settings);
    }

    @Override
    public Enrichment enrich(List<Entry> entries) {
        return Enrichment.of(entries);
    }
}
