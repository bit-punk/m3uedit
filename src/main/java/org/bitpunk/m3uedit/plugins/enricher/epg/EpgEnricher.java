package org.bitpunk.m3uedit.plugins.enricher.epg;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.AbstractPlugin;
import org.bitpunk.m3uedit.plugins.enricher.Enricher;
import org.bitpunk.m3uedit.plugins.enricher.Enrichment;
import org.bitpunk.m3uedit.util.EntryMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assigns the entries of the playlist to the channels of programme guides (EPG sources in XMLTV format) by setting
 * their tvg-id, so players show the programme of each channel.
 * <p>
 * The configuration is part of the project. The sources are cached locally, see {@link EpgSourceCache}.
 */
@Getter
@Setter
@Slf4j
public class EpgEnricher extends AbstractPlugin implements Enricher {
    static final int DEFAULT_CACHE_HOURS = 12;
    static final String NO_EPG_DATA = "no EPG data";

    /**
     * The channels read from a cached source, kept as long as the file does not change.
     */
    private record CachedChannels(FileTime lastModified, long size, List<XmltvChannel> channels) {
    }

    private static final Map<Path, CachedChannels> CHANNELS = new ConcurrentHashMap<>();

    @JsonIgnore
    private final String name = "EPG Enricher";
    @JsonIgnore
    private final String description = "Sets the tvg-id of the entries to the channels of EPG sources (XMLTV)";

    /**
     * The urls of the XMLTV files, in the order of their priority.
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

    @JsonIgnore
    private transient EpgSourceCache sourceCache;

    public EpgEnricher() {
    }

    EpgEnricher(EpgSourceCache sourceCache) {
        this.sourceCache = sourceCache;
    }

    private EpgSourceCache getSourceCache() {
        if (sourceCache == null) {
            sourceCache = new EpgSourceCache();
        }
        return sourceCache;
    }

    @Override
    public Enrichment enrich(List<Entry> entries) {
        List<String> urls = sources == null ? List.of()
                : sources.stream().filter(Objects::nonNull).map(String::strip).filter(url -> !url.isEmpty()).distinct().toList();
        if (urls.isEmpty()) {
            return new Enrichment(entries, List.of(), null, "No EPG sources configured.", null, null);
        }
        List<String> warnings = new ArrayList<>();
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
                } catch (IOException e) {
                    log.warn("EPG source {} cannot be read: {}", url, e.getMessage());
                    warnings.add("%s cannot be read: %s".formatted(url, e.getMessage()));
                }
            }
        }
        EpgChannelIndex index = new EpgChannelIndex(channelsPerSource);
        EpgMatcher matcher = new EpgMatcher(index, strategies == null ? List.of() : strategies,
                urlRules == null ? List.of() : urlRules);

        List<Entry> enriched = new ArrayList<>(entries.size());
        Map<Entry, String> notes = new IdentityHashMap<>();
        int withEpgData = 0;
        for (Entry entry : entries) {
            EpgMatcher.Match match = matcher.match(entry);
            if (match == null) {
                enriched.add(entry);
                notes.put(entry, NO_EPG_DATA);
                continue;
            }
            if (match.inSources()) {
                withEpgData++;
            }
            Entry result = entry;
            if (!match.channelId().equals(entry.getTvgId())) {
                result = EntryMapper.copyOf(entry);
                result.setTvgId(match.channelId());
                notes.put(result, "by " + match.strategy().getLabel());
            }
            if (!match.inSources()) {
                notes.put(result, "by %s, not in the EPG sources".formatted(match.strategy().getLabel()));
            }
            enriched.add(result);
        }
        String summary = "%d of %d entries have EPG data (%d channels in %d of %d sources).".formatted(withEpgData,
                entries.size(), index.size(), channelsPerSource.size(), urls.size());
        return new Enrichment(enriched, List.of(), notes, summary, warnings, urls);
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
        });
    }
}
