package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.enricher.Enrichment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EpgEnricherTest {

    @TempDir
    Path tempDir;
    private EpgEnricher enricher;
    private String source;

    private static Entry entry(String name, String tvgId, String url) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setTvgId(tvgId);
        entry.setChannelUri(url);
        return entry;
    }

    @BeforeEach
    void setUp() throws IOException {
        enricher = new EpgEnricher(new EpgSourceCache(tempDir.resolve("cache"), HttpClient.newHttpClient(),
                Clock.systemUTC()));
        source = XmltvTestFiles.write(tempDir.resolve("guide.xml"), XmltvTestFiles.GUIDE).toUri().toString();
        enricher.setSources(List.of(source));
    }

    @Test
    void testEntriesAreMatchedByTvgIdNameAndUrlRule() {
        Entry byTvgId = entry("Something", "ZDF.de", "http://example.com/1");
        Entry byTvgIdCase = entry("Other", "daserste.DE", "http://example.com/2");
        Entry byName = entry("DE: arte HD", null, "http://example.com/3");
        Entry byRule = entry("Channel 4", null, "rtsp://fritz.box/?service=514");
        Entry unmatched = entry("Unknown TV", "Unknown.de", "http://example.com/5");
        enricher.setUrlRules(List.of(new UrlRule("service=514\\b", "ZDF.de")));

        Enrichment enrichment = enricher.enrich(List.of(byTvgId, byTvgIdCase, byName, byRule, unmatched));

        List<Entry> entries = enrichment.entries();
        assertThat(entries.get(0)).as("already correct").isSameAs(byTvgId);
        assertThat(entries.get(1).getTvgId()).isEqualTo("DasErste.de");
        assertThat(entries.get(2).getTvgId()).isEqualTo("arte.de");
        assertThat(entries.get(3).getTvgId()).isEqualTo("ZDF.de");
        assertThat(entries.get(4)).isSameAs(unmatched);
        assertThat(byTvgIdCase.getTvgId()).as("input not modified").isEqualTo("daserste.DE");
        assertThat(enrichment.notes()).containsEntry(entries.get(1), "by tvg-id")
                .containsEntry(entries.get(2), "by name")
                .containsEntry(entries.get(3), "by URL rule")
                .containsEntry(unmatched, EpgEnricher.NO_EPG_DATA)
                .doesNotContainKey(byTvgId);
        assertThat(enrichment.summary()).isEqualTo("4 of 5 entries have EPG data (4 channels in 1 of 1 sources).");
        assertThat(enrichment.epgUrls()).containsExactly(source);
        assertThat(enrichment.warnings()).isEmpty();
    }

    @Test
    void testOnlySelectedStrategiesAreApplied() {
        enricher.setStrategies(List.of(MatchStrategy.TVG_ID));

        Enrichment enrichment = enricher.enrich(List.of(entry("ZDF HD", null, "http://example.com/zdf")));

        assertThat(enrichment.entries().getFirst().getTvgId()).isNull();
    }

    @Test
    void testTvgIdNotInTheSourcesIsReplacedByName() {
        Enrichment enrichment = enricher.enrich(List.of(entry("ZDF HD", "zdf.hd", "http://example.com/zdf")));

        assertThat(enrichment.entries().getFirst().getTvgId()).isEqualTo("ZDF.de");
    }

    @Test
    void testUrlRuleWithUnknownIdIsAppliedButNotCounted() {
        enricher.setStrategies(List.of(MatchStrategy.URL_RULE));
        enricher.setUrlRules(List.of(new UrlRule("example", "Unknown.de")));

        Enrichment enrichment = enricher.enrich(List.of(entry("A", null, "http://example.com/a")));

        Entry enriched = enrichment.entries().getFirst();
        assertThat(enriched.getTvgId()).isEqualTo("Unknown.de");
        assertThat(enrichment.notes()).containsEntry(enriched, "by URL rule, not in the EPG sources");
        assertThat(enrichment.summary()).startsWith("0 of 1 entries");
    }

    @Test
    void testFirstSourceWinsAndUnreadableSourcesAreReported() throws IOException {
        String second = XmltvTestFiles.write(tempDir.resolve("second.xml"), XmltvTestFiles.guide("ZDF.at", "ORF1.at"))
                .toUri().toString();
        String broken = XmltvTestFiles.write(tempDir.resolve("broken.xml"), "no xml").toUri().toString();
        enricher.setSources(List.of(source, second, broken, tempDir.resolve("missing.xml").toUri().toString()));

        Enrichment enrichment = enricher.enrich(List.of(entry("ZDF", null, "http://e/1"), entry("ORF1", null, "http://e/2")));

        assertThat(enrichment.entries()).extracting(Entry::getTvgId).containsExactly("ZDF.de", "ORF1.at");
        assertThat(enrichment.warnings()).hasSize(2)
                .anySatisfy(warning -> assertThat(warning).contains("broken.xml cannot be read"))
                .anySatisfy(warning -> assertThat(warning).contains("missing.xml cannot be read"));
        assertThat(enrichment.summary()).contains("in 2 of 4 sources");
    }

    @Test
    void testWithoutSourcesNothingIsChanged() {
        enricher.setSources(List.of());
        Entry entry = entry("ZDF", null, "http://e/1");

        Enrichment enrichment = enricher.enrich(List.of(entry));

        assertThat(enrichment.entries()).containsExactly(entry);
        assertThat(enrichment.summary()).isEqualTo("No EPG sources configured.");
        assertThat(enrichment.epgUrls()).isEmpty();
    }

    @Test
    void testNamePrefixIsTriedLast() throws IOException {
        enricher.setSources(List.of(XmltvTestFiles.write(tempDir.resolve("regional.xml"), """
                <tv>
                  <channel id="WDR.de"><display-name>WDR</display-name></channel>
                  <channel id="WDR.Koeln.de"><display-name>WDR Köln</display-name></channel>
                  <channel id="SWR/SR.de"><display-name>SWR/SR</display-name></channel>
                </tv>
                """).toUri().toString()));

        Enrichment enrichment = enricher.enrich(List.of(entry("WDR HD Köln", null, "http://e/1"),
                entry("WDR HD Aachen", null, "http://e/2"), entry("SR Fernsehen HD", null, "http://e/3"),
                entry("SWR HD", null, "http://e/4")));

        assertThat(enrichment.entries()).extracting(Entry::getTvgId)
                .containsExactly("WDR.Koeln.de", "WDR.de", "SWR/SR.de", "SWR/SR.de");
        assertThat(enrichment.entries()).extracting(entry -> enrichment.notes().get(entry))
                .containsExactly("by name", "by name prefix", "by name prefix", "by name");
    }

    @Test
    void testNamePrefixCanBeSwitchedOff() {
        enricher.setStrategies(List.of(MatchStrategy.TVG_ID, MatchStrategy.NAME));

        Enrichment enrichment = enricher.enrich(List.of(entry("ZDF Österreich", null, "http://e/1")));

        assertThat(enrichment.entries().getFirst().getTvgId()).isNull();
    }
}
