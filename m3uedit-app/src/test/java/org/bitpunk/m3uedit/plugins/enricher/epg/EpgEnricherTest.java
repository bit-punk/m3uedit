package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.bitpunk.m3uedit.api.Enrichment;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.Programme;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.plugins.DefaultPluginContext;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.bitpunk.m3uedit.testsupport.TestPluginSupport;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class EpgEnricherTest {
    /**
     * The test guide contains a programme on 2026-11-13 20:00 (+0100).
     */
    static final Clock NOW = Clock.fixed(Instant.parse("2026-11-13T18:00:00Z"), ZoneId.of("Europe/Berlin"));

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
        enricher = TestPluginSupport.create(() -> new EpgEnricher(new EpgSourceCache(XmltvTestFiles.CONTEXT,
                tempDir.resolve("cache"), HttpClient.newHttpClient(), Clock.systemUTC()), NOW));
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
                .containsEntry(unmatched, "no EPG data")
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

    private static String compile(Enrichment enrichment, List<Entry> playlist) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        enrichment.attachments().getFirst().content().write(playlist, outputStream);
        byte[] bytes = outputStream.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0x1f && (bytes[1] & 0xff) == 0x8b) {
            bytes = new GZIPInputStream(new ByteArrayInputStream(bytes)).readAllBytes();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    void testGuideWithTheChannelsOfThePlaylistIsProvided() throws IOException {
        Enrichment enrichment = enricher.enrich(List.of(entry("Das Erste HD", null, "http://e/1")));

        assertThat(enrichment.attachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.name()).isEqualTo("EPG guide");
            assertThat(attachment.suffix()).isEqualTo(".xml.gz");
            assertThat(attachment.headerAttributes()).containsExactly("x-tvg-url", "url-tvg");
        });
        String guide = compile(enrichment, enrichment.entries());
        assertThat(guide).contains("<channel id=\"DasErste.de\">", "<title lang=\"de\">Tagesschau</title>",
                        "Nachrichten &amp; Wetter")
                .doesNotContain("ZDF.de", "arte.de", "Late.de");
        Path written = XmltvTestFiles.write(tempDir.resolve("written.xml"), guide);
        assertThat(XmltvFiles.readChannels(XmltvTestFiles.CONTEXT, written)).containsExactly(new XmltvChannel("DasErste.de", List.of("Das Erste")));
    }

    @Test
    void testGuideIsCompiledFromThePlaylistWritten() throws IOException {
        enricher.setCompressGuide(false);
        Enrichment enrichment = enricher.enrich(List.of(entry("Das Erste HD", null, "http://e/1")));
        // e.g. set manually by the user after the enrichment, with another spelling
        Entry manual = entry("Mein Sender", "zdf.DE", "http://e/2");

        String guide = compile(enrichment, List.of(enrichment.entries().getFirst(), manual));

        assertThat(enrichment.attachments().getFirst().suffix()).isEqualTo(".xml");
        assertThat(guide).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .contains("<channel id=\"DasErste.de\">", "<channel id=\"zdf.DE\">");
    }

    @Test
    void testNoGuideIfSwitchedOffOrWithoutChannels() {
        enricher.setCompileGuide(false);
        assertThat(enricher.enrich(List.of(entry("ZDF", null, "http://e/1"))).attachments()).isEmpty();

        enricher.setCompileGuide(true);
        enricher.setSources(List.of(tempDir.resolve("missing.xml").toUri().toString()));
        assertThat(enricher.enrich(List.of(entry("ZDF", null, "http://e/1"))).attachments()).isEmpty();
    }

    @Test
    void testTimeWindowOfTheGuide() throws IOException {
        enricher.setCompressGuide(false);
        enricher.setGuideDaysAhead(0);
        enricher.setGuideHoursPast(1);
        Enrichment enrichment = enricher.enrich(List.of(entry("Das Erste", null, "http://e/1")));
        assertThat(compile(enrichment, enrichment.entries())).as("programme in 1 hour").contains("Tagesschau");

        EpgEnricher later = TestPluginSupport.create(() -> new EpgEnricher(new EpgSourceCache(XmltvTestFiles.CONTEXT,
                tempDir.resolve("cache"), HttpClient.newHttpClient(), Clock.systemUTC()),
                Clock.offset(NOW, Duration.ofHours(3))));
        later.setSources(List.of(source));
        later.setCompressGuide(false);
        later.setGuideHoursPast(1);
        Enrichment laterEnrichment = later.enrich(List.of(entry("Das Erste", null, "http://e/1")));
        assertThat(compile(laterEnrichment, laterEnrichment.entries())).as("ended 1:45 hours ago")
                .doesNotContain("Tagesschau").contains("<channel id=\"DasErste.de\">");
    }

    @Test
    void testProgrammesOfThePlaylistAreProvided() throws IOException {
        Entry erste = entry("Das Erste HD", "daserste.de", "http://e/1");
        Entry zdf = entry("ZDF HD", "ZDF.de", "http://e/2");
        Entry unknown = entry("Unknown", null, "http://e/3");

        List<Programme> programmes = enricher.readProgrammes(List.of(erste, zdf, unknown));

        assertThat(programmes).singleElement().satisfies(programme -> {
            assertThat(programme.channelId()).as("as used by the playlist").isEqualTo("daserste.de");
            assertThat(programme.channelName()).isEqualTo("Das Erste HD");
            assertThat(programme.title()).isEqualTo("Tagesschau");
            assertThat(programme.description()).isEqualTo("Nachrichten & Wetter");
        });
    }

    @Test
    void testNewEnricherHasTheDefaultSources() {
        EpgEnricher enricher = TestPluginSupport.create(EpgEnricher::new);

        assertThat(enricher.getSources()).containsExactly(
                "https://epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz",
                "https://epgshare01.online/epgshare01/epg_ripper_AT1.xml.gz",
                "https://epgshare01.online/epgshare01/epg_ripper_CH1.xml.gz");
        assertThat(PluginStates.enabled(enricher).get()).as("nothing is downloaded until enabled").isFalse();
        // the defaults are copied, changing the sources of one enricher does not change them
        enricher.getSources().clear();
        assertThat(TestPluginSupport.create(EpgEnricher::new).getSources()).hasSize(3);
    }

    /**
     * @return a context reading the configuration files of the plugins from the given directory
     */
    private static PluginContext contextWithConfigDirectory(Path directory) {
        return new DefaultPluginContext(EpgEnricher.class) {
            @Override
            public Path configDirectory() {
                try {
                    return Files.createDirectories(directory);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
        };
    }

    @Test
    void testDefaultSourcesAreReadFromTheConfigurationFile() throws IOException {
        Path file = tempDir.resolve("plugins").resolve("epg_enricher.yaml");
        PluginContext context = contextWithConfigDirectory(tempDir.resolve("plugins"));

        // created from the defaults of the application on first use
        assertThat(EpgEnricher.defaultSources(context)).hasSize(3)
                .allSatisfy(url -> assertThat(url).startsWith("https://epgshare01.online/"));
        assertThat(file).exists().content().contains("epg_ripper_DE1.xml.gz");

        Files.writeString(file, """
                defaultSources:
                  - "https://epg.example.org/uk.xml.gz"
                  - " "
                  - "file:///home/user/guide.xml"
                  - "https://epg.example.org/uk.xml.gz"
                """);
        assertThat(EpgEnricher.defaultSources(context))
                .containsExactly("https://epg.example.org/uk.xml.gz", "file:///home/user/guide.xml");

        Files.writeString(file, "otherSetting: 1\n");
        assertThat(EpgEnricher.defaultSources(context)).as("no sources configured").isEmpty();
    }

    @Test
    void testSettingsAreReturnedAndSet() {
        EpgEnricher source = TestPluginSupport.create(EpgEnricher::new);
        source.setSources(List.of("https://epg.example.org/de.xml.gz"));
        source.setStrategies(List.of(MatchStrategy.TVG_ID, MatchStrategy.URL_RULE));
        source.setUrlRules(List.of(new UrlRule("service=514", "ZDF.de")));
        source.setCacheHours(6);
        source.setCompileGuide(false);
        source.setGuideDaysAhead(0);
        source.setGuideHoursPast(2);
        source.setCompressGuide(false);

        assertThat(source.getSettingsType()).isEqualTo(EpgEnricherSettings.class);
        assertThat(source.getSettings()).isEqualTo(new EpgEnricherSettings(List.of("https://epg.example.org/de.xml.gz"),
                List.of(MatchStrategy.TVG_ID, MatchStrategy.URL_RULE), List.of(new UrlRule("service=514", "ZDF.de")),
                6, false, 0, 2, false));

        EpgEnricher target = TestPluginSupport.create(EpgEnricher::new);
        target.setSettings(source.getSettings());
        assertThat(target.getSettings()).isEqualTo(source.getSettings());
        assertThat(target.getSources()).containsExactly("https://epg.example.org/de.xml.gz");
        assertThat(target.getGuideDaysAhead()).as("0 keeps all programmes").isZero();
    }

    @Test
    void testMissingSettingsGetTheDefaults() {
        EpgEnricher enricher = TestPluginSupport.create(EpgEnricher::new);

        enricher.setSettings(new EpgEnricherSettings(null, null, null, null, null, null, null, null));

        assertThat(enricher.getSources()).containsExactlyElementsOf(EpgEnricher.defaultSources(XmltvTestFiles.CONTEXT));
        assertThat(enricher.getStrategies()).containsExactly(MatchStrategy.values());
        assertThat(enricher.getUrlRules()).isEmpty();
        assertThat(enricher.getCacheHours()).isEqualTo(EpgEnricher.DEFAULT_CACHE_HOURS);
        assertThat(enricher.isCompileGuide()).isTrue();
        assertThat(enricher.getGuideDaysAhead()).isEqualTo(EpgEnricher.DEFAULT_GUIDE_DAYS_AHEAD);
        assertThat(enricher.getGuideHoursPast()).isEqualTo(EpgEnricher.DEFAULT_GUIDE_HOURS_PAST);
        assertThat(enricher.isCompressGuide()).isTrue();

        enricher.setSettings(new EpgEnricherSettings(List.of(), null, null, null, null, null, null, null));
        assertThat(enricher.getSources()).as("cleared by the user").isEmpty();
    }

    @Test
    void testSettingsAreReadFromAProjectFile() throws IOException {
        Path projectFile = tempDir.resolve("epg.m3uproj");
        Files.writeString(projectFile, """
                name: epg
                enrichers:
                - type: EpgEnricher
                  enabled: true
                  sources:
                  - https://epg.example.org/de.xml.gz
                  strategies:
                  - TVG_ID
                  - URL_RULE
                  urlRules:
                  - regex: service=514
                    channelId: ZDF.de
                  cacheHours: 6
                  compileGuide: false
                  guideDaysAhead: 3
                  guideHoursPast: 0
                  compressGuide: false
                - type: EpgEnricher
                  cacheHours: 2
                """);

        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);

        assertThat(loaded.getEnrichers()).hasSize(2);
        EpgEnricher configured = (EpgEnricher) loaded.getEnrichers().get(0);
        assertThat(PluginStates.enabled(configured).get()).isTrue();
        assertThat(configured.getSettings()).isEqualTo(new EpgEnricherSettings(
                List.of("https://epg.example.org/de.xml.gz"), List.of(MatchStrategy.TVG_ID, MatchStrategy.URL_RULE),
                List.of(new UrlRule("service=514", "ZDF.de")), 6, false, 3, 0, false));
        EpgEnricher old = (EpgEnricher) loaded.getEnrichers().get(1);
        assertThat(old.getSources()).containsExactlyElementsOf(EpgEnricher.defaultSources(XmltvTestFiles.CONTEXT));
        assertThat(old.getStrategies()).containsExactly(MatchStrategy.values());
        assertThat(old.getCacheHours()).isEqualTo(2);
        assertThat(old.isCompileGuide()).isTrue();
        assertThat(old.getGuideDaysAhead()).isEqualTo(EpgEnricher.DEFAULT_GUIDE_DAYS_AHEAD);
        assertThat(old.isCompressGuide()).isTrue();
    }
}
