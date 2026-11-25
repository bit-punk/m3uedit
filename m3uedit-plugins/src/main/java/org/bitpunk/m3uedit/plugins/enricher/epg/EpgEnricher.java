package org.bitpunk.m3uedit.plugins.enricher.epg;

import javafx.stage.Window;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Attachment;
import org.bitpunk.m3uedit.api.CollectedData;
import org.bitpunk.m3uedit.api.Enricher;
import org.bitpunk.m3uedit.api.Enrichment;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.api.Programme;
import org.bitpunk.m3uedit.api.ProgrammeGuideProvider;
import org.bitpunk.m3uedit.plugins.enricher.epg.model.EpgEnricherConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

/**
 * Assigns the entries of the playlist to the channels of programme guides (EPG sources in XMLTV format) by setting
 * their tvg-id, so players show the programme of each channel.
 * <p>
 * When the playlist is written, a programme guide with only the channels of the playlist is written next to it
 * (optional), see {@link GuideCompiler}.
 * <p>
 * The configuration is part of the project. The sources are cached locally, see {@link EpgSourceCache}.
 */
@Getter
@Setter
@Slf4j
@PluginType(id = "EpgEnricher", nameKey = "epgEnricher.name", texts = "org.bitpunk.m3uedit.plugins.messages")
public class EpgEnricher implements Enricher, ProgrammeGuideProvider {
    static final int DEFAULT_CACHE_HOURS = 12;
    static final int DEFAULT_GUIDE_DAYS_AHEAD = 7;
    static final int DEFAULT_GUIDE_HOURS_PAST = 6;
    static final String CONFIG_FILE_NAME = "epg_enricher.yaml";
    static final String CONFIG_RESOURCE = "plugin/enricher/epg/epg_enricher.yaml";

    /**
     * The channels read from a cached source, kept as long as the file does not change.
     */
    private record CachedChannels(FileTime lastModified, long size, List<XmltvChannel> channels) {
    }

    /**
     * The sources available locally, with their channels.
     */
    private record LoadedSources(List<Path> files, List<List<XmltvChannel>> channels, List<String> warnings) {
        EpgChannelIndex index() {
            return new EpgChannelIndex(channels);
        }
    }

    private static final Map<Path, CachedChannels> CHANNELS = new ConcurrentHashMap<>();

    /**
     * The urls of the XMLTV files, in the order of their priority. A new enricher and project files without sources
     * (e.g. written before the enricher existed) get the default sources; an empty list saved by the user stays empty.
     */
    private List<String> sources = new ArrayList<>();
    /**
     * The strategies applied, always in the order of {@link MatchStrategy}.
     */
    private List<MatchStrategy> strategies = new ArrayList<>(Arrays.asList(MatchStrategy.values()));
    private List<UrlRule> urlRules = new ArrayList<>();
    /**
     * The age of a downloaded source after which it is downloaded again.
     */
    private int cacheHours = DEFAULT_CACHE_HOURS;
    /**
     * Whether a programme guide with the channels of the playlist is written next to it.
     */
    private boolean compileGuide = true;
    /**
     * Programmes starting later are left out of the guide written, 0 to keep all.
     */
    private int guideDaysAhead = DEFAULT_GUIDE_DAYS_AHEAD;
    /**
     * Programmes ended earlier are left out of the guide written, 0 to keep all.
     */
    private int guideHoursPast = DEFAULT_GUIDE_HOURS_PAST;
    private boolean compressGuide = true;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private PluginContext context;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private EpgSourceCache sourceCache;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Clock clock;

    public EpgEnricher() {
    }

    EpgEnricher(EpgSourceCache sourceCache, Clock clock) {
        this.sourceCache = sourceCache;
        this.clock = clock;
    }

    @Override
    public void init(PluginContext context) {
        this.context = context;
        sources = new ArrayList<>(defaultSources(context));
    }

    /**
     * @return the name shown to the user, not part of a project-file
     */
    @Override
    public String getName() {
        return context.text("epgEnricher.name");
    }

    /**
     * @return the downloaded EPG sources
     */
    @Override
    public List<CollectedData> getCollectedData() {
        return List.of(EpgCache.collectedData(context));
    }

    @Override
    public String getDescription() {
        return context.text("epgEnricher.description");
    }

    @Override
    public Class<? extends Record> getSettingsType() {
        return EpgEnricherSettings.class;
    }

    @Override
    public Record getSettings() {
        return new EpgEnricherSettings(sources, strategies, urlRules, cacheHours, compileGuide, guideDaysAhead,
                guideHoursPast, compressGuide);
    }

    /**
     * Missing sources (e.g. a project written before the enricher existed) are replaced by the default sources, an
     * empty list (cleared by the user) stays empty.
     */
    @Override
    public void setSettings(Record settings) {
        EpgEnricherSettings epgSettings = (EpgEnricherSettings) settings;
        sources = new ArrayList<>(epgSettings.sources() != null ? epgSettings.sources() : defaultSources(context));
        strategies = new ArrayList<>(epgSettings.strategies() != null ? epgSettings.strategies()
                : Arrays.asList(MatchStrategy.values()));
        urlRules = new ArrayList<>(epgSettings.urlRules() != null ? epgSettings.urlRules() : List.of());
        cacheHours = Objects.requireNonNullElse(epgSettings.cacheHours(), DEFAULT_CACHE_HOURS);
        compileGuide = Objects.requireNonNullElse(epgSettings.compileGuide(), true);
        guideDaysAhead = Objects.requireNonNullElse(epgSettings.guideDaysAhead(), DEFAULT_GUIDE_DAYS_AHEAD);
        guideHoursPast = Objects.requireNonNullElse(epgSettings.guideHoursPast(), DEFAULT_GUIDE_HOURS_PAST);
        compressGuide = Objects.requireNonNullElse(epgSettings.compressGuide(), true);
    }

    /**
     * Returns the sources a new enricher starts with, configured in {@code ~/.m3uedit/plugins/epg_enricher.yaml}
     * (created with the free programme guides of epgshare01 for Germany, Austria and Switzerland). Read on each call,
     * so changes of the file take effect without restart.
     */
    public static List<String> defaultSources(PluginContext context) {
        EpgEnricherConfig config = context.loadConfig(CONFIG_FILE_NAME, EpgEnricherConfig.class, CONFIG_RESOURCE,
                EpgEnricherConfig::new);
        if (config == null || config.getDefaultSources() == null) {
            return List.of();
        }
        return config.getDefaultSources().stream().filter(Objects::nonNull).map(String::strip)
                .filter(url -> !url.isEmpty()).distinct().toList();
    }

    private Clock getClock() {
        return clock != null ? clock : Clock.systemDefaultZone();
    }

    private EpgSourceCache getSourceCache() {
        if (sourceCache == null) {
            sourceCache = new EpgSourceCache(context);
        }
        return sourceCache;
    }

    @Override
    public Enrichment enrich(List<Entry> entries) {
        List<String> urls = configuredSources();
        if (urls.isEmpty()) {
            return new Enrichment(entries, List.of(), null, context.text("epgEnricher.summary.noSources"), null, null);
        }
        LoadedSources loaded = loadSources(urls);
        List<String> warnings = loaded.warnings();
        List<List<XmltvChannel>> channelsPerSource = loaded.channels();
        EpgChannelIndex index = loaded.index();
        EpgMatcher matcher = new EpgMatcher(index, strategies == null ? List.of() : strategies,
                urlRules == null ? List.of() : urlRules);

        List<Entry> enriched = new ArrayList<>(entries.size());
        Map<Entry, String> notes = new IdentityHashMap<>();
        int withEpgData = 0;
        for (Entry entry : entries) {
            EpgMatcher.Match match = matcher.match(entry);
            if (match == null) {
                enriched.add(entry);
                notes.put(entry, context.text("epgEnricher.note.noEpgData"));
                continue;
            }
            if (match.inSources()) {
                withEpgData++;
            }
            Entry result = entry;
            if (!match.channelId().equals(entry.getTvgId())) {
                result = entry.copy();
                result.setTvgId(match.channelId());
                notes.put(result, context.text("epgEnricher.note.matched", match.strategy().getLabel(context)));
            }
            if (!match.inSources()) {
                notes.put(result, context.text("epgEnricher.note.notInSources", match.strategy().getLabel(context)));
            }
            enriched.add(result);
        }
        String summary = context.text("epgEnricher.summary", withEpgData, entries.size(), index.size(),
                channelsPerSource.size(), urls.size());
        List<Attachment> attachments = compileGuide && index.size() > 0 ? List.of(guideAttachment(urls)) : List.of();
        return new Enrichment(enriched, attachments, notes, summary, warnings, urls);
    }

    private LoadedSources loadSources(List<String> urls) {
        List<String> warnings = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        List<List<XmltvChannel>> channelsPerSource = new ArrayList<>();
        Duration maxAge = Duration.ofHours(Math.max(1, cacheHours));
        for (String url : urls) {
            EpgSourceCache.CachedSource source = getSourceCache().get(url, maxAge);
            if (source.warning() != null) {
                warnings.add(source.warning());
            }
            if (source.file() != null) {
                try {
                    channelsPerSource.add(channelsOf(source.file(), context));
                    files.add(source.file());
                } catch (IOException e) {
                    log.warn("EPG source {} cannot be read: {}", url, e.getMessage());
                    warnings.add(context.text("epgEnricher.warning.unreadable", url, e.getMessage()));
                }
            }
        }
        return new LoadedSources(files, channelsPerSource, warnings);
    }

    /**
     * The guide is compiled when the playlist is written, from the playlist written (including the manual changes
     * of the user), with the configuration at the time of the enrichment.
     */
    private Attachment guideAttachment(List<String> urls) {
        Duration ahead = guideDaysAhead > 0 ? Duration.ofDays(guideDaysAhead) : null;
        Duration past = guideHoursPast > 0 ? Duration.ofHours(guideHoursPast) : null;
        boolean compressed = compressGuide;
        Clock compileClock = getClock();
        return new Attachment(context.text("epgEnricher.guide.name"), compressed ? ".xml.gz" : ".xml", List.of("x-tvg-url", "url-tvg"),
                (playlist, outputStream) -> {
                    LoadedSources loaded = loadSources(urls);
                    List<GuideCompiler.SourceSelection> selections = selectChannels(playlist, loaded);
                    GuideCompiler compiler = new GuideCompiler(context, compileClock.instant(), past, ahead, compileClock.getZone());
                    if (compressed) {
                        GZIPOutputStream gzip = new GZIPOutputStream(outputStream, 64 * 1024);
                        compiler.write(selections, gzip);
                        gzip.finish();
                    } else {
                        compiler.write(selections, outputStream);
                    }
                });
    }

    @Override
    public List<Programme> readProgrammes(List<Entry> playlist) throws IOException {
        LoadedSources loaded = loadSources(configuredSources());
        Map<String, String> names = new HashMap<>();
        for (Entry entry : playlist) {
            if (entry.getTvgId() != null && !entry.getTvgId().isBlank()) {
                names.putIfAbsent(entry.getTvgId().strip(), entry.getName());
            }
        }
        List<Programme> programmes = new ArrayList<>();
        for (GuideCompiler.SourceSelection selection : selectChannels(playlist, loaded)) {
            programmes.addAll(XmltvFiles.readProgrammes(context, selection.file(), selection.channels(), names,
                    getClock().getZone()));
        }
        programmes.sort(Comparator.comparing(Programme::start, Comparator.nullsLast(Comparator.naturalOrder())));
        return programmes;
    }

    private List<String> configuredSources() {
        return sources == null ? List.of()
                : sources.stream().filter(Objects::nonNull).map(String::strip).filter(url -> !url.isEmpty()).distinct().toList();
    }

    /**
     * Selects the channels referenced by the tvg-ids of the playlist from the sources they are taken from.
     * A tvg-id differing from the id of the source only by its case is written as used by the playlist.
     */
    static List<GuideCompiler.SourceSelection> selectChannels(List<Entry> playlist, LoadedSources loaded) {
        EpgChannelIndex index = loaded.index();
        List<Map<String, String>> channelsPerSource = new ArrayList<>();
        loaded.files().forEach(file -> channelsPerSource.add(new LinkedHashMap<>()));
        for (Entry entry : playlist) {
            String sourceId = index.findId(entry.getTvgId());
            int source = index.sourceOf(sourceId);
            if (source >= 0) {
                channelsPerSource.get(source).putIfAbsent(sourceId, entry.getTvgId().strip());
            }
        }
        List<GuideCompiler.SourceSelection> selections = new ArrayList<>();
        for (int source = 0; source < loaded.files().size(); source++) {
            if (!channelsPerSource.get(source).isEmpty()) {
                selections.add(new GuideCompiler.SourceSelection(loaded.files().get(source), channelsPerSource.get(source)));
            }
        }
        return selections;
    }

    /**
     * Reading the channels of a large source takes a few seconds, so they are kept until the file changes.
     */
    private static List<XmltvChannel> channelsOf(Path file, PluginContext context) throws IOException {
        FileTime lastModified = Files.getLastModifiedTime(file);
        long size = Files.size(file);
        CachedChannels cached = CHANNELS.get(file);
        if (cached != null && cached.lastModified().equals(lastModified) && cached.size() == size) {
            return cached.channels();
        }
        List<XmltvChannel> channels = XmltvFiles.readChannels(context, file);
        log.debug("{} channels read from {}", channels.size(), file);
        CHANNELS.put(file, new CachedChannels(lastModified, size, channels));
        return channels;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean configure(Window owner) {
        EpgEnricherConfigDialog dialog = new EpgEnricherConfigDialog(this, context);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        Optional<EpgEnricherConfigDialog.Config> result = dialog.showAndWait();
        result.ifPresent(config -> {
            sources = new ArrayList<>(config.sources());
            strategies = new ArrayList<>(config.strategies());
            urlRules = new ArrayList<>(config.urlRules());
            cacheHours = config.cacheHours();
            compileGuide = config.compileGuide();
            guideDaysAhead = config.guideDaysAhead();
            guideHoursPast = config.guideHoursPast();
            compressGuide = config.compressGuide();
        });
        return result.isPresent();
    }
}
