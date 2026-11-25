package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.util.ProjectPersisterUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PluginTypesTest {

    /**
     * The ids the plugins are stored with in existing project-files: they must never change.
     */
    private static final Map<String, String> IDS = Map.ofEntries(
            Map.entry("org.bitpunk.m3uedit.plugins.input.file.FileInput", "FileInput"),
            Map.entry("org.bitpunk.m3uedit.plugins.input.fritzbox.FritzBoxInput", "FritzBoxInput"),
            Map.entry("org.bitpunk.m3uedit.plugins.input.url.UrlInput", "UrlInput"),
            Map.entry("org.bitpunk.m3uedit.plugins.input.iptvorg.IptvOrgInput", "IptvOrgInput"),
            Map.entry("org.bitpunk.m3uedit.plugins.input.sftp.SftpInput", "SftpInput"),
            Map.entry("org.bitpunk.m3uedit.plugins.output.file.FileOutput", "FileOutput"),
            Map.entry("org.bitpunk.m3uedit.plugins.output.sftp.SftpOutput", "SftpOutput"),
            Map.entry("org.bitpunk.m3uedit.plugins.filter.channelname.ChannelNameFilter", "ChannelNameFilter"),
            Map.entry("org.bitpunk.m3uedit.plugins.filter.sdchannel.SdChannelFilter", "SdChannelFilter"),
            Map.entry("org.bitpunk.m3uedit.plugins.filter.uniquename.UniqueNameFilter", "UniqueNameFilter"),
            Map.entry("org.bitpunk.m3uedit.plugins.filter.uniqueuri.UniqueUriFilter", "UniqueUriFilter"),
            Map.entry("org.bitpunk.m3uedit.plugins.filter.reachableurl.ReachableUrlFilter", "ReachableUrlFilter"),
            Map.entry("org.bitpunk.m3uedit.plugins.sorter.channelname.ChannelNameSorter", "ChannelNameSorter"),
            Map.entry("org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.PreferredChannelOrderSorter",
                    "PreferredChannelOrderSorter"),
            Map.entry("org.bitpunk.m3uedit.plugins.enricher.epg.EpgEnricher", "EpgEnricher"));

    @TempDir
    Path tempDir;

    @AfterEach
    void english() {
        Messages.use(Locale.ENGLISH);
    }

    private static List<PluginDescriptor<?>> all() {
        return Stream.of(InputPluginService.getInstance(), OutputPluginService.getInstance(),
                        FilterPluginService.getInstance(), SorterPluginService.getInstance(),
                        EnricherPluginService.getInstance())
                .<PluginDescriptor<?>>flatMap(service -> service.getDescriptors().stream()).toList();
    }

    @Test
    void testThePluginsKeepTheIdsOfExistingProjectFiles() {
        Map<String, String> ids = new LinkedHashMap<>();
        all().forEach(plugin -> ids.put(plugin.type().getName(), plugin.id()));

        assertThat(ids).isEqualTo(IDS);
    }

    @Test
    void testEveryPluginDeclaresItsType() {
        assertThat(all()).allSatisfy(plugin -> {
            PluginType type = plugin.type().getAnnotation(PluginType.class);
            assertThat(type).as(plugin.type().getName()).isNotNull();
            assertThat(type.nameKey()).as(plugin.type().getName()).isNotBlank();
        });
    }

    @Test
    void testTheNameIsTheOneOfThePluginInEachLanguage() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.GERMAN)) {
            Messages.use(locale);
            assertThat(all()).allSatisfy(plugin -> assertThat(plugin.name()).as(plugin.id())
                    .isEqualTo(plugin.create().getName()));
        }
    }

    @Test
    void testEachPluginCreatesANewInstance() {
        assertThat(all()).allSatisfy(plugin -> {
            Plugin first = plugin.create();
            assertThat(first).isInstanceOf(plugin.type()).isNotSameAs(plugin.create());
        });
    }

    // ---------- type ids of other plugins ----------

    @PluginType(id = "de.example.DuplicateInput")
    static class Duplicate extends TestPlugin {
    }

    @PluginType(id = "de.example.DuplicateInput")
    static class OtherDuplicate extends TestPlugin {
    }

    static class WithoutType extends TestPlugin {
    }

    private static <T extends Plugin> PluginDescriptor<T> describe(Class<T> type) {
        return PluginDescriptor.of(new ServiceLoader.Provider<>() {
            @Override
            public Class<? extends T> type() {
                return type;
            }

            @Override
            public T get() {
                try {
                    return type.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    @Test
    void testAPluginWithTheIdOfAnEarlierOneIsReportedAndIgnored() {
        List<String> errors = new ArrayList<>();

        List<PluginDescriptor<?>> unique = PluginDescriptor.withUniqueIds(
                List.of(describe(Duplicate.class), describe(OtherDuplicate.class), describe(WithoutType.class)),
                errors::add);

        assertThat(unique).<Class<?>>extracting(PluginDescriptor::type)
                .containsExactly(Duplicate.class, WithoutType.class);
        assertThat(errors).singleElement().asString()
                .contains(OtherDuplicate.class.getName(), "de.example.DuplicateInput", Duplicate.class.getName());
    }

    @Test
    void testTheSamePluginFoundTwiceIsNoError() {
        List<String> errors = new ArrayList<>();

        List<PluginDescriptor<?>> unique = PluginDescriptor.withUniqueIds(
                List.of(describe(Duplicate.class), describe(Duplicate.class)), errors::add);

        assertThat(unique).hasSize(1);
        assertThat(errors).isEmpty();
    }

    @Test
    void testAPluginWithoutTypeIsIdentifiedByItsClassName() {
        PluginDescriptor<WithoutType> plugin = describe(WithoutType.class);

        assertThat(plugin.id()).isEqualTo("WithoutType");
        // without the key of its name, the plugin itself names it
        assertThat(plugin.name()).isEqualTo("test plugin");
    }

    // ---------- project-files ----------

    @Test
    void testAProjectFileWithEveryPluginIsWrittenAndReadWithTheirIds() throws Exception {
        // with every filter, sorter and enricher
        DataModel dataModel = DataModelFactory.createDataModel("all plugins");
        dataModel.getInputs().addAll(InputPluginService.getInstance().getAllPlugins());
        dataModel.getOutputs().addAll(OutputPluginService.getInstance().getAllPlugins());
        Path projectFile = tempDir.resolve("all.m3uproj");

        ProjectPersisterUtil.saveProjectFile(projectFile.toString(), dataModel);
        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);

        String yaml = Files.readString(projectFile);
        assertThat(IDS.values()).allSatisfy(id -> assertThat(yaml).contains("type: " + id + "\n"));
        List<Plugin> plugins = new ArrayList<>();
        plugins.addAll(loaded.getInputs());
        plugins.addAll(loaded.getOutputs());
        plugins.addAll(loaded.getFilters());
        plugins.addAll(loaded.getSorters());
        plugins.addAll(loaded.getEnrichers());
        assertThat(plugins).extracting(plugin -> plugin.getClass().getName())
                .containsExactlyInAnyOrderElementsOf(IDS.keySet());
    }

    @Test
    void testAProjectFileOfTheCurrentVersionIsRead() throws Exception {
        Path projectFile = tempDir.resolve("old.m3uproj");
        Files.copy(Path.of("src/test/resources/project/testproject.m3uproj"), projectFile);

        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getInputs()).extracting(plugin -> plugin.getClass().getSimpleName())
                .containsExactly("FileInput", "FritzBoxInput");
        assertThat(loaded.getFilters()).extracting(plugin -> plugin.getClass().getSimpleName())
                .contains("ChannelNameFilter", "UniqueNameFilter", "SdChannelFilter");
        assertThat(loaded.getOutputs()).extracting(plugin -> plugin.getClass().getSimpleName())
                .containsExactly("FileOutput");
    }
}
