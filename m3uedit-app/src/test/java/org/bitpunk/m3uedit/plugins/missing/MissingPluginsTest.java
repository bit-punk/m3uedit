package org.bitpunk.m3uedit.plugins.missing;

import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.Input;
import org.bitpunk.m3uedit.api.Playlist;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.bitpunk.m3uedit.util.ProjectPersisterUtilAccess;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Projects using plugins that are not installed: the plugins are kept as placeholders, written back unchanged and do
 * not take part in the computation.
 */
class MissingPluginsTest {

    private static final String PROJECT = """
            name: other computer
            inputs:
              - type: de.example.BoxInput
                enabled: true
                host: box.local
                channels: [1, 2, 3]
            filters:
              - type: de.example.RadioFilter
                enabled: true
                keepRadio: false
            sorters:
              - type: de.example.Sorter
                enabled: false
            enrichers:
              - type: de.example.LogoEnricher
                enabled: true
                logos:
                  ard: http://example.com/ard.png
            outputs:
              - type: de.example.MediaServerOutput
                enabled: true
                server: {host: nas, port: 8096}
            """;

    private static ObjectMapper mapper() {
        return ProjectPersisterUtilAccess.mapper();
    }

    private static DataModel load(String yaml) {
        DataModel project = mapper().readValue(yaml, DataModel.class);
        project.setBackgroundExecutor(Runnable::run);
        project.setFxExecutor(Runnable::run);
        return project;
    }

    private static Entry entry(String name) {
        Entry entry = new Entry();
        entry.setName(name);
        entry.setChannelUri("http://example.com/" + name);
        return entry;
    }

    /**
     * An input delivering the given entries.
     */
    private static Input input(Entry... entries) {
        return new Input() {
            @Override
            public Playlist read() {
                return Playlist.of(List.of(entries));
            }

            @Override
            public String getSource() {
                return "test";
            }

            @Override
            public String getName() {
                return "Test Input";
            }

            @Override
            public String getDescription() {
                return "";
            }
        };
    }

    @Test
    void testAProjectWithPluginsThatAreNotInstalledIsOpened() {
        DataModel project = load(PROJECT);

        assertThat(project.getInputs()).singleElement().isInstanceOf(MissingInput.class);
        assertThat(project.getFilters()).singleElement().isInstanceOf(MissingFilter.class);
        assertThat(project.getSorters()).singleElement().isInstanceOf(MissingSorter.class);
        assertThat(project.getEnrichers()).singleElement().isInstanceOf(MissingEnricher.class);
        assertThat(project.getOutputs()).singleElement().isInstanceOf(MissingOutput.class);
        assertThat(project.getMissingPlugins()).extracting(MissingPlugin::getTypeId).containsExactly(
                "de.example.BoxInput", "de.example.RadioFilter", "de.example.Sorter", "de.example.LogoEnricher",
                "de.example.MediaServerOutput");
        assertThat(PluginStates.enabled(project.getFilters().getFirst()).get()).isTrue();
        assertThat(PluginStates.enabled(project.getSorters().getFirst()).get()).isFalse();
        MissingPlugin input = project.getMissingPlugins().getFirst();
        assertThat(input.getName()).isEqualTo("de.example.BoxInput");
        assertThat(input.isConfigurable()).isFalse();
    }

    @Test
    void testThePluginsAreWrittenBackUnchanged() {
        DataModel project = load(PROJECT);

        String saved = mapper().writeValueAsString(project);

        JsonNode original = mapper().readTree(PROJECT);
        JsonNode written = mapper().readTree(saved);
        for (String list : List.of("inputs", "filters", "sorters", "enrichers", "outputs")) {
            assertThat(written.get(list)).as(list).isEqualTo(original.get(list));
        }
    }

    @Test
    void testMissingFiltersSortersAndEnrichersAreSkipped() {
        DataModel project = load(PROJECT);
        project.getInputs().setAll(input(entry("b"), entry("a")));
        List<String> errors = new ArrayList<>();
        project.setErrorHandler(errors::add);

        project.reloadInputs();

        // the enabled filter and enricher are not applied, the entries are unchanged
        assertThat(project.getSortedEntryList()).extracting(Entry::getName).containsExactly("b", "a");
        assertThat(project.getPipelineResult().steps()).isEmpty();
        assertThat(errors).isEmpty();
    }

    @Test
    void testAMissingInputIsReportedAsInputThatCouldNotBeRead() {
        DataModel project = load(PROJECT);
        project.getInputs().add(input(entry("a")));

        project.reloadInputs();

        assertThat(project.getSortedEntryList()).extracting(Entry::getName).containsExactly("a");
        assertThat(project.getInputErrors()).singleElement().asString()
                .contains("de.example.BoxInput", "is not installed");
    }

    @Test
    void testAMissingOutputIsReportedWhenWriting() {
        DataModel project = load(PROJECT);
        project.getInputs().setAll(input(entry("a")));
        List<String> errors = new ArrayList<>();
        project.setErrorHandler(errors::add);
        project.reloadInputs();

        project.saveEntries();

        assertThat(errors).anySatisfy(error -> assertThat(error).contains("de.example.MediaServerOutput",
                "is not installed"));
    }

    @Test
    void testMissingFiltersAreKeptWhenThePluginsAreCompleted() {
        DataModel project = load("""
                name: two
                filters:
                  - type: de.example.First
                  - type: de.example.Second
                """);

        DataModelFactory.completePlugins(project);

        assertThat(project.getFilters()).filteredOn(MissingPlugin.class::isInstance)
                .extracting(filter -> ((MissingPlugin) filter).getTypeId())
                .containsExactly("de.example.First", "de.example.Second");
        // and the built-in filters are added as usual
        assertThat(project.getFilters()).hasSizeGreaterThan(2).allSatisfy(filter ->
                assertThat(filter).isInstanceOf(Filter.class));
    }
}
