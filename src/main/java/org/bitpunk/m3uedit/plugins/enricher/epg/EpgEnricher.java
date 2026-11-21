package org.bitpunk.m3uedit.plugins.enricher.epg;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.config.AppConfigPaths;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.enricher.Attachment;
import org.bitpunk.m3uedit.plugins.enricher.Enricher;
import org.bitpunk.m3uedit.plugins.enricher.Enrichment;
import org.bitpunk.m3uedit.plugins.enricher.Programme;
import org.bitpunk.m3uedit.plugins.enricher.ProgrammeGuideProvider;
import org.bitpunk.m3uedit.plugins.enricher.epg.model.EpgEnricherConfig;
import org.bitpunk.m3uedit.util.EntryMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class EpgEnricher extends AbstractPlugin implements Enricher, ProgrammeGuideProvider {
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
     * @return the name shown to the user, not part of a project-file
     */
    @Override
    public String getName() {
        return Messages.get("epgEnricher.name");
    }

    @Override
    public String getDescription() {
        return Messages.get("epgEnricher.description");
    }

    /**
     * The urls of the XMLTV files, in the order of their priority. Project files without sources (e.g. written before
     * the enricher existed) get the default sources; an empty list saved by the user stays empty.
     */
    private List<String> sources = new ArrayList<>(defaultSources());
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

    @JsonIgnore
    private transient EpgSourceCache sourceCache;
    @JsonIgnore
    private transient Clock clock;

    public EpgEnricher() {
    }

    EpgEnricher(EpgSourceCache sourceCache, Clock clock) {
        this.sourceCache = sourceCache;
        this.clock = clock;
    }

    /**
     * Returns the sources a new enricher starts with, configured in {@code ~/.m3uedit/plugins/epg_enricher.yaml}
     * (created with the free programme guides of epgshare01 for Germany, Austria and Switzerland). Read on each call,
     * so changes of the file take effect without restart.
     */
    public static List<String> defaultSources() {
        return defaultSources(new YamlConfigManager<>(AppConfigPaths.getPluginConfigPath(CONFIG_FILE_NAME),
                CONFIG_RESOURCE, EpgEnricherConfig.class, EpgEnricherConfig::new));
    }

    static List<String> defaultSources(YamlConfigManager<EpgEnricherConfig> configManager) {
        EpgEnricherConfig config = configManager.loadConfig();
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
            sourceCache = new EpgSourceCache();
        }
        return sourceCache;
    }

    @Override
    public Enrichment enrich(List<Entry> entries) {
        List<String> urls = configuredSources();
        if (urls.isEmpty()) {
            return new Enrichment(entries, List.of(), null, Messages.get("epgEnricher.summary.noSources"), null, null);
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
                notes.put(entry, Messages.get("epgEnricher.note.noEpgData"));
                continue;
            }
            if (match.inSources()) {
                withEpgData++;
            }
            Entry result = entry;
            if (!match.channelId().equals(entry.getTvgId())) {
                result = EntryMapper.copyOf(entry);
                result.setTvgId(match.channelId());
                notes.put(result, Messages.get("epgEnricher.note.matched", match.strategy().getLabel()));
            }
            if (!match.inSources()) {
                notes.put(result, Messages.get("epgEnricher.note.notInSources", match.strategy().getLabel()));
            }
            enriched.add(result);
        }
        String summary = Messages.get("epgEnricher.summary", withEpgData, entries.size(), index.size(),
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
                    channelsPerSource.add(channelsOf(source.file()));
                    files.add(source.file());
                } catch (IOException e) {
                    log.warn("EPG source {} cannot be read: {}", url, e.getMessage());
                    warnings.add(Messages.get("epgEnricher.warning.unreadable", url, e.getMessage()));
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
        return new Attachment(Messages.get("epgEnricher.guide.name"), compressed ? ".xml.gz" : ".xml", List.of("x-tvg-url", "url-tvg"),
                (playlist, outputStream) -> {
                    LoadedSources loaded = loadSources(urls);
                    List<GuideCompiler.SourceSelection> selections = selectChannels(playlist, loaded);
                    GuideCompiler compiler = new GuideCompiler(compileClock.instant(), past, ahead, compileClock.getZone());
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
            programmes.addAll(XmltvFiles.readProgrammes(selection.file(), selection.channels(), names,
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
    private static List<XmltvChannel> channelsOf(Path file) throws IOException {
        FileTime lastModified = Files.getLastModifiedTime(file);
        long size = Files.size(file);
        CachedChannels cached = CHANNELS.get(file);
        if (cached != null && cached.lastModified().equals(lastModified) && cached.size() == size) {
            return cached.channels();
        }
        List<XmltvChannel> channels = XmltvFiles.readChannels(file);
        log.debug("{} channels read from {}", channels.size(), file);
        CHANNELS.put(file, new CachedChannels(lastModified, size, channels));
        return channels;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void configure() {
        new EpgEnricherConfigDialog(this).showAndWait().ifPresent(config -> {
            sources = new ArrayList<>(config.sources());
            strategies = new ArrayList<>(config.strategies());
            urlRules = new ArrayList<>(config.urlRules());
            cacheHours = config.cacheHours();
            compileGuide = config.compileGuide();
            guideDaysAhead = config.guideDaysAhead();
            guideHoursPast = config.guideHoursPast();
            compressGuide = config.compressGuide();
        });
    }
}
