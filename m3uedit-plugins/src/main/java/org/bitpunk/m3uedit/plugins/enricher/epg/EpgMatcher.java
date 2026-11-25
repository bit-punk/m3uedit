package org.bitpunk.m3uedit.plugins.enricher.epg;

import org.bitpunk.m3uedit.api.Entry;

import java.util.List;

/**
 * Assigns the entries of a playlist to the channels of a programme guide.
 */
final class EpgMatcher {

    /**
     * The channel found for an entry.
     *
     * @param channelId the id of the channel, which becomes the tvg-id of the entry
     * @param strategy  the strategy that found the channel
     * @param inSources false if the id is not contained in the sources (only possible for url rules)
     */
    record Match(String channelId, MatchStrategy strategy, boolean inSources) {
    }

    private final EpgChannelIndex index;
    private final List<MatchStrategy> strategies;
    private final List<UrlRule> urlRules;

    /**
     * @param strategies the strategies to apply, in the order of {@link MatchStrategy}
     */
    EpgMatcher(EpgChannelIndex index, List<MatchStrategy> strategies, List<UrlRule> urlRules) {
        this.index = index;
        this.strategies = strategies.stream().distinct().sorted().toList();
        this.urlRules = List.copyOf(urlRules);
    }

    /**
     * @return the channel of the first strategy finding one, null if no strategy finds a channel
     */
    Match match(Entry entry) {
        for (MatchStrategy strategy : strategies) {
            Match match = switch (strategy) {
                case TVG_ID -> matchTvgId(entry);
                case URL_RULE -> matchUrlRules(entry);
                case NAME -> matchName(entry);
                case NAME_PREFIX -> matchNamePrefix(entry);
            };
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private Match matchTvgId(Entry entry) {
        String id = index.findId(entry.getTvgId());
        return id == null ? null : new Match(id, MatchStrategy.TVG_ID, true);
    }

    private Match matchUrlRules(Entry entry) {
        for (UrlRule rule : urlRules) {
            String id = rule.channelIdFor(entry.getChannelUri());
            if (id != null) {
                // the user decides: a rule assigns its id even if the sources do not (yet) contain it
                String knownId = index.findId(id);
                return new Match(knownId != null ? knownId : id, MatchStrategy.URL_RULE, knownId != null);
            }
        }
        return null;
    }

    private Match matchNamePrefix(Entry entry) {
        for (String name : new String[]{entry.getTvgName(), entry.getName()}) {
            String id = index.findIdByNamePrefix(name);
            if (id != null) {
                return new Match(id, MatchStrategy.NAME_PREFIX, true);
            }
        }
        return null;
    }

    private Match matchName(Entry entry) {
        for (String name : new String[]{entry.getTvgName(), entry.getName()}) {
            String id = index.findIdByName(name);
            if (id == null) {
                // a tvg-name may also be written like an id, e.g. "ZDF.de"
                id = index.findId(name);
            }
            if (id != null) {
                return new Match(id, MatchStrategy.NAME, true);
            }
        }
        return null;
    }
}
