package org.bitpunk.m3uedit.plugins;

import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Playlist;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.config.AppConfigPaths;
import org.bitpunk.m3uedit.config.YamlConfigManager;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.EntryList;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.parser.ParsingException;
import org.bitpunk.m3uedit.parser.Writer;
import org.bitpunk.m3uedit.util.DialogUtil;
import org.bitpunk.m3uedit.util.HeadlessMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.Supplier;

/**
 * The context of a plugin, see {@link PluginContext}: implemented by the services of m3uedit.
 */
@Slf4j
public class DefaultPluginContext implements PluginContext {
    private final Class<?> pluginType;

    /**
     * @param pluginType the class of the plugin, whose {@link PluginType#texts()} and class loader are used
     */
    public DefaultPluginContext(Class<?> pluginType) {
        this.pluginType = pluginType;
    }

    @Override
    public String text(String key, Object... arguments) {
        String text;
        try {
            text = texts().getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
        return arguments.length == 0 ? text : new MessageFormat(text, locale()).format(arguments);
    }

    @Override
    public ResourceBundle resourceBundle() {
        return texts();
    }

    private ResourceBundle texts() {
        PluginType type = pluginType.getAnnotation(PluginType.class);
        if (type == null || type.texts().isBlank()) {
            // a plugin without texts of its own (e.g. in tests)
            return Messages.bundle();
        }
        // without falling back to the language of the system, like the texts of m3uedit
        return ResourceBundle.getBundle(type.texts(), locale(), pluginType.getClassLoader(),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    @Override
    public Locale locale() {
        return Messages.locale();
    }

    @Override
    public Playlist readPlaylist(InputStream stream) throws IOException {
        try {
            EntryList entries = new Parser().parse(stream);
            return new Playlist(entries, entries.getEpgUrls());
        } catch (ParsingException e) {
            throw new IOException(e.getMessage(), e);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public void writePlaylist(List<Entry> entries, Map<String, String> headerAttributes, OutputStream stream) {
        new Writer().write(entries, headerAttributes, stream);
    }

    @Override
    public Path configDirectory() {
        return createDirectories(AppConfigPaths.getPluginsDir());
    }

    @Override
    public Path cacheDirectory() {
        return createDirectories(AppConfigPaths.getBaseDir().resolve("cache"));
    }

    private static Path createDirectories(Path directory) {
        try {
            return Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public <T> T loadConfig(String fileName, Class<T> type, Supplier<T> defaults) {
        return new YamlConfigManager<>(configDirectory().resolve(fileName), type, defaults).loadConfig();
    }

    @Override
    public <T> T loadConfig(String fileName, Class<T> type, String defaultsResource, Supplier<T> defaults) {
        Path file = configDirectory().resolve(fileName);
        if (!Files.exists(file) && defaultsResource != null) {
            // read with the class loader of the plugin, which may be another one than the one of m3uedit
            try (InputStream resource = pluginType.getClassLoader().getResourceAsStream(defaultsResource)) {
                if (resource != null) {
                    Files.copy(resource, file);
                } else {
                    log.warn("resource {} of plugin {} not found", defaultsResource, pluginType.getName());
                }
            } catch (IOException e) {
                log.error("could not create {} from {}", file, defaultsResource, e);
            }
        }
        return loadConfig(fileName, type, defaults);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void saveConfig(String fileName, Object config) {
        new YamlConfigManager<>(configDirectory().resolve(fileName), (Class<Object>) config.getClass()).saveConfig(config);
    }

    @Override
    public boolean isHeadless() {
        return HeadlessMode.isActive();
    }

    @Override
    public void showError(String message) {
        if (isHeadless()) {
            log.error(message);
        } else if (Platform.isFxApplicationThread()) {
            DialogUtil.showError(message);
        } else {
            Platform.runLater(() -> DialogUtil.showError(message));
        }
    }
}
