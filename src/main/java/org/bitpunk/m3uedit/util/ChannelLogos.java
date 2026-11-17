package org.bitpunk.m3uedit.util;

import javafx.scene.image.Image;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The logos of the channels (tvg-logo), loaded in the background and kept, so table cells re-used while scrolling
 * do not load them again. Only to be used on the JavaFX application thread.
 */
@Slf4j
public final class ChannelLogos {
    /**
     * The number of logos kept, the least recently used are dropped.
     */
    static final int MAX_CACHED = 1000;

    private static final Map<String, Image> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MAX_CACHED;
        }
    };

    private ChannelLogos() {
        // Utility class
    }

    /**
     * @param url    the url of the logo
     * @param width  the width the logo is shown with at most
     * @param height the height the logo is shown with at most
     * @return the logo scaled to fit into the given size, loaded in the background; null if there is no url or it
     * is invalid. A logo that cannot be loaded is an image with an error, which shows nothing.
     */
    public static Image get(String url, double width, double height) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String key = url.strip() + '|' + width + 'x' + height;
        Image logo = CACHE.get(key);
        if (logo == null) {
            try {
                logo = new Image(url.strip(), width, height, true, true, true);
            } catch (IllegalArgumentException e) {
                log.debug("invalid logo url {}", url);
                return null;
            }
            CACHE.put(key, logo);
        }
        return logo;
    }

    static void clear() {
        CACHE.clear();
    }
}
