package org.bitpunk.m3uedit.plugins.enricher.epg;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes channel names, so the names of a playlist match the display names of a programme guide
 * despite differences in spelling, e.g. "DE: ZDF HD" and "ZDF", or "Das Erste" and "das-erste".
 */
final class ChannelNames {
    /**
     * A country prefix as common in IPTV playlists, e.g. "DE: ", "DE | ", "[DE] ".
     */
    private static final Pattern COUNTRY_PREFIX = Pattern.compile("^(\\[[a-z]{2,3}]|[a-z]{2,3}\\s*[:|])\\s*");
    /**
     * Quality and format markers, which are not part of the channel name.
     */
    private static final Pattern QUALITY = Pattern.compile(
            "\\b(hd|sd|fhd|uhd|4k|8k|hevc|h\\.?26[45]|\\d{3,4}p|\\d{2}fps|hdr|raw|backup|vip)\\b");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^a-z0-9]+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private ChannelNames() {
    }

    /**
     * @return the normalized name: lower case, without country prefix, quality markers, accents, blanks and
     * punctuation; empty if nothing is left (or the name is null)
     */
    static String normalize(String name) {
        return NON_ALPHANUMERIC.matcher(simplify(name)).replaceAll("");
    }

    /**
     * Returns the normalized prefixes of a name made of its first words, longest first, without the whole name:
     * "WDR HD Köln" gives "wdr" (regional variants of a channel often only differ by a suffix).
     */
    static List<String> prefixes(String name) {
        List<String> words = Arrays.stream(WORD_SEPARATOR.split(simplify(name))).filter(word -> !word.isEmpty()).toList();
        List<String> prefixes = new ArrayList<>();
        for (int count = words.size() - 1; count >= 1; count--) {
            prefixes.add(String.join("", words.subList(0, count)));
        }
        return prefixes;
    }

    /**
     * Display names may name several channels sharing a programme, e.g. "SWR/SR".
     *
     * @return the name and, if it is made of several names separated by '/', each of them
     */
    static List<String> alternatives(String displayName) {
        List<String> names = new ArrayList<>();
        names.add(displayName);
        if (displayName != null && displayName.contains("/")) {
            Arrays.stream(displayName.split("/")).map(String::strip).filter(part -> !part.isEmpty()).forEach(names::add);
        }
        return names;
    }

    private static String simplify(String name) {
        if (name == null) {
            return "";
        }
        String simplified = name.strip().toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        simplified = COUNTRY_PREFIX.matcher(simplified).replaceFirst("");
        simplified = QUALITY.matcher(simplified).replaceAll(" ");
        simplified = DIACRITICS.matcher(Normalizer.normalize(simplified, Normalizer.Form.NFD)).replaceAll("");
        return simplified.replace("&", " and ").replace("+", " plus ");
    }
}
