package org.bitpunk.m3uedit.plugins.enricher.epg.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * The configuration of the EPG enricher shared by all projects, stored in
 * {@code ~/.m3uedit/plugins/epg_enricher.yaml}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpgEnricherConfig {
    /**
     * The sources a new EPG enricher starts with, in the order of their priority.
     */
    @Builder.Default
    private List<String> defaultSources = new ArrayList<>();
}
