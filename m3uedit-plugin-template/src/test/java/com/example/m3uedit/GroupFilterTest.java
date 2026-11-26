package com.example.m3uedit;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;
import org.bitpunk.m3uedit.api.Playlist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the plugin without m3uedit: the API is all it needs, the context is a small stand-in.
 */
class GroupFilterTest {
    private static final String TEXTS = GroupFilter.class.getAnnotation(PluginType.class).texts();

    private GroupFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GroupFilter();
        filter.init(new TestContext(Locale.ENGLISH));
    }

    @Test
    void testANewFilterRemovesNothing() {
        List<Entry> entries = List.of(entry("News", "a"), entry("Sports", "b"));

        assertEquals(entries, filter.filter(entries));
    }

    @Test
    void testRemovesTheEntriesOfTheGroupsIgnoringCase() {
        Entry news = entry("News", "a");
        Entry sports = entry("Sports", "b");
        Entry noGroup = entry(null, "c");
        filter.setSettings(new GroupFilterSettings(List.of("sports "), false));

        assertEquals(List.of(news, noGroup), filter.filter(List.of(news, sports, noGroup)));
        assertEquals("group \"Sports\"", filter.describeRemoval(sports, List.of(news, sports, noGroup)));
    }

    @Test
    void testKeepsOnlyTheEntriesOfTheGroups() {
        Entry news = entry("News", "a");
        Entry sports = entry("Sports", "b");
        filter.setSettings(new GroupFilterSettings(List.of("News"), true));

        assertEquals(List.of(news), filter.filter(List.of(news, sports)));
        assertEquals("Keeps only the entries of 1 group(s)", filter.getDescription());
    }

    @Test
    void testSettingsMissingInAnOlderProjectFileAreTheDefaults() {
        filter.setSettings(new GroupFilterSettings(null, null));

        assertEquals(GroupFilterSettings.defaults(), filter.getSettings());
        assertEquals(List.of(), filter.getSettings().groups());
        assertFalse(filter.getSettings().keepGroups());
    }

    @Test
    void testIsRegisteredForTheServiceLoader() {
        assertTrue(ServiceLoader.load(Filter.class).stream().anyMatch(provider -> provider.type() == GroupFilter.class));
    }

    @Test
    void testTheTextsExistInEnglishAndGerman() {
        ResourceBundle english = bundle(Locale.ENGLISH);
        ResourceBundle german = bundle(Locale.GERMAN);

        assertEquals(english.keySet(), german.keySet());
        assertTrue(english.containsKey(GroupFilter.class.getAnnotation(PluginType.class).nameKey()));
        assertEquals("Gruppen-Filter", german.getString("groupFilter.name"));
    }

    private static ResourceBundle bundle(Locale locale) {
        // without the fallback to the default locale of the machine running the test
        return ResourceBundle.getBundle(TEXTS, locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    private static Entry entry(String group, String name) {
        Entry entry = new Entry();
        entry.setGroupTitle(group);
        entry.setName(name);
        entry.setChannelUri("http://example.com/" + name);
        return entry;
    }

    /**
     * The texts of the plugin, as m3uedit provides them; everything else is not needed by this plugin.
     */
    private record TestContext(Locale locale) implements PluginContext {

        @Override
        public String text(String key, Object... arguments) {
            String text = resourceBundle().getString(key);
            return arguments.length == 0 ? text : new MessageFormat(text, locale).format(arguments);
        }

        @Override
        public ResourceBundle resourceBundle() {
            return bundle(locale);
        }

        @Override
        public Playlist readPlaylist(InputStream stream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writePlaylist(List<Entry> entries, Map<String, String> headerAttributes, OutputStream stream) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path configDirectory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path cacheDirectory() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T loadConfig(String fileName, Class<T> type, Supplier<T> defaults) {
            return defaults.get();
        }

        @Override
        public <T> T loadConfig(String fileName, Class<T> type, String defaultsResource, Supplier<T> defaults) {
            return defaults.get();
        }

        @Override
        public void saveConfig(String fileName, Object config) {
        }

        @Override
        public boolean isHeadless() {
            return true;
        }

        @Override
        public void showError(String message) {
        }
    }
}
