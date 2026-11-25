package org.bitpunk.m3uedit.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What a plugin gets from m3uedit, passed to {@link Plugin#init(PluginContext)}.
 */
public interface PluginContext {

    /**
     * @return the text of the key from the resource bundle of the plugin (see {@link PluginType#texts()}) in the
     * language of the user interface, formatted by {@link java.text.MessageFormat} if there are arguments; the key
     * itself if it is missing. The command-line mode always uses English.
     */
    String text(String key, Object... arguments);

    /**
     * @return the language of the user interface, e.g. to format dates
     */
    Locale locale();

    /**
     * Reads an M3U playlist.
     *
     * @throws IOException if the stream cannot be read or does not contain an M3U playlist
     */
    Playlist readPlaylist(InputStream stream) throws IOException;

    /**
     * Writes an M3U playlist (UTF-8).
     *
     * @param headerAttributes the attributes of the {@code #EXTM3U} header, e.g. from
     *                         {@link Export#headerAttributes(java.util.function.Function)}
     */
    void writePlaylist(List<Entry> entries, Map<String, String> headerAttributes, OutputStream stream)
            throws IOException;

    /**
     * @return the directory of the configuration files of the plugins ({@code ~/.m3uedit/plugins}), created if needed
     */
    Path configDirectory();

    /**
     * @return the directory for files the plugin downloads or computes and can create again
     * ({@code ~/.m3uedit/cache}), created if needed
     */
    Path cacheDirectory();

    /**
     * Reads a configuration file of the plugin, a YAML file in {@link #configDirectory()} which the user may also edit
     * by hand. If it does not exist, it is created with the defaults.
     *
     * @param fileName the name of the file, e.g. {@code channel_name_filter.yaml}
     * @param type     the class of the configuration, mapped by its fields
     * @param defaults creates the configuration used if the file does not exist or cannot be read
     */
    <T> T loadConfig(String fileName, Class<T> type, Supplier<T> defaults);

    /**
     * Like {@link #loadConfig(String, Class, Supplier)}, with defaults read from a YAML resource of the plugin.
     *
     * @param defaultsResource the path of the resource, e.g. {@code plugin/filter/channel_name_filter.yaml}
     */
    <T> T loadConfig(String fileName, Class<T> type, String defaultsResource, Supplier<T> defaults);

    /**
     * Writes a configuration file of the plugin, see {@link #loadConfig(String, Class, Supplier)}.
     */
    void saveConfig(String fileName, Object config);

    /**
     * @return true while m3uedit runs without user interface (command-line mode): the plugin must not open dialogs
     */
    boolean isHeadless();

    /**
     * Shows an error to the user in a dialog, on the JavaFX application thread; logged only in the command-line mode.
     */
    void showError(String message);
}
