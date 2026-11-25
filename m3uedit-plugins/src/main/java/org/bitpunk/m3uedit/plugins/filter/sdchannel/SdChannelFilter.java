package org.bitpunk.m3uedit.plugins.filter.sdchannel;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes SD channels: a channel is an SD channel if the same channel exists with a 'HD' suffix,
 * e.g. "ZDF" is removed if "ZDF HD" exists. Other channels starting with the same name, like "ZDFneo" or
 * "RTL ZWEI", do not make a channel an SD channel.
 */
@PluginType(id = "SdChannelFilter", nameKey = "filter.sdChannel.name", texts = "org.bitpunk.m3uedit.plugins.messages")
public class SdChannelFilter implements Filter {
    /**
     * A channel name with HD suffix ("ZDF HD", "ZDFHD", "ZDF (HD)", "Bingo-TV ᴴᴰ"), group 1 is the name without it.
     */
    private static final Pattern HD_NAME = Pattern.compile("^(.*?)\\s*(?:HD|\\(HD\\)|ᴴᴰ)$", Pattern.CASE_INSENSITIVE);

    private PluginContext context;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    /**
     * @return the name shown to the user, not part of a project-file
     */
    @Override
    public String getName() {
        return context.text("filter.sdChannel.name");
    }

    @Override
    public String getDescription() {
        return context.text("filter.sdChannel.description");
    }

    @Override
    public int order() {
        return ORDER_VARIANTS;
    }

    @Override
    public List<Entry> filter(List<Entry> list) {
        Set<String> namesWithHdVariant = new HashSet<>();
        for (Entry entry : list) {
            sdNameOf(entry).ifPresent(namesWithHdVariant::add);
        }
        return list.stream()
                .filter(entry -> !namesWithHdVariant.contains(normalize(entry.getName())))
                .toList();
    }

    @Override
    public String describeRemoval(Entry removed, List<Entry> input) {
        String removedName = normalize(removed.getName());
        return input.stream()
                .filter(entry -> sdNameOf(entry).filter(removedName::equals).isPresent())
                .findFirst()
                .map(entry -> context.text("filter.sdChannel.reason", entry.getName()))
                .orElse(null);
    }

    /**
     * @return the normalized name of the SD variant of an HD channel, empty if the entry is no HD channel
     */
    private static Optional<String> sdNameOf(Entry entry) {
        if (entry.getName() == null) {
            return Optional.empty();
        }
        Matcher matcher = HD_NAME.matcher(entry.getName().strip());
        if (!matcher.matches() || matcher.group(1).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(normalize(matcher.group(1)));
    }

    private static String normalize(String channelName) {
        return channelName == null ? "" : channelName.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Plugins are compared by identity, only a short description is returned.
     */
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), getName());
    }
}
