package org.bitpunk.m3uedit.plugins.enricher.epg;

import java.util.List;

/**
 * The settings of the {@link EpgEnricher} stored in the project-file. Settings missing in the file (e.g. written by an
 * older version) are null and replaced by the defaults of the enricher.
 *
 * @param sources        the urls of the XMLTV files, in the order of their priority; null if missing (the default
 *                       sources are used), an empty list if cleared by the user
 * @param strategies     the strategies applied
 * @param urlRules       the rules assigning channel ids to stream urls
 * @param cacheHours     the age of a downloaded source after which it is downloaded again
 * @param compileGuide   whether a programme guide with the channels of the playlist is written next to it
 * @param guideDaysAhead programmes starting later are left out of the guide written, 0 to keep all
 * @param guideHoursPast programmes ended earlier are left out of the guide written, 0 to keep all
 * @param compressGuide  whether the guide written is compressed (gzip)
 */
public record EpgEnricherSettings(List<String> sources, List<MatchStrategy> strategies, List<UrlRule> urlRules,
                                  Integer cacheHours, Boolean compileGuide, Integer guideDaysAhead,
                                  Integer guideHoursPast, Boolean compressGuide) {
}
