package org.bitpunk.m3uedit.util;

import org.bitpunk.m3uedit.api.Enricher;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.Input;
import org.bitpunk.m3uedit.api.Output;
import org.bitpunk.m3uedit.api.Sorter;
import org.bitpunk.m3uedit.jackson.PluginModule;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.DataModelFactory;
import org.bitpunk.m3uedit.plugins.AbstractPluginService;
import org.bitpunk.m3uedit.plugins.PluginJars;
import org.bitpunk.m3uedit.plugins.PluginService;
import org.bitpunk.m3uedit.plugins.PluginStates;
import org.bitpunk.m3uedit.testsupport.ExamplePluginJar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A plugin installed as jar is stored in project-files like a built-in one.
 */
class InstalledPluginProjectTest {

    @TempDir
    Path extensions;

    private static PluginModule.Services services(PluginJars jars) {
        PluginService<Input> inputs = AbstractPluginService.of(Input.class, jars);
        PluginService<Filter> filters = AbstractPluginService.of(Filter.class, jars);
        PluginService<Sorter> sorters = AbstractPluginService.of(Sorter.class, jars);
        PluginService<Enricher> enrichers = AbstractPluginService.of(Enricher.class, jars);
        PluginService<Output> outputs = AbstractPluginService.of(Output.class, jars);
        return new PluginModule.Services(() -> inputs, () -> filters, () -> sorters, () -> enrichers, () -> outputs);
    }

    @Test
    void testTheSettingsOfAnInstalledPluginAreStoredAndRead() throws Exception {
        ExamplePluginJar.build(extensions, "radio-filter.jar");
        PluginModule.Services services = services(PluginJars.load(extensions, getClass().getClassLoader()));
        ObjectMapper mapper = ProjectPersisterUtil.mapperFor(services);
        Filter radio = services.filters().get().find(ExamplePluginJar.RADIO_FILTER).orElseThrow().create();
        // its settings record is only known by the jar: RadioFilterSettings(Boolean keepRadio)
        radio.setSettings(radio.getSettingsType().getConstructor(Boolean.class).newInstance(true));
        PluginStates.enabled(radio).set(true);
        DataModel project = DataModelFactory.createDataModel("radio");
        project.getFilters().add(radio);

        String yaml = mapper.writeValueAsString(project);
        DataModel loaded = mapper.readValue(yaml, DataModel.class);

        assertThat(yaml).contains("type: " + ExamplePluginJar.RADIO_FILTER, "keepRadio: true");
        Filter loadedRadio = loaded.getFilters().stream()
                .filter(filter -> filter.getClass().getName().equals(ExamplePluginJar.RADIO_FILTER))
                .findFirst().orElseThrow();
        assertThat(loadedRadio.getSettings()).isEqualTo(radio.getSettings());
        assertThat(PluginStates.enabled(loadedRadio).get()).isTrue();
    }

    @Test
    void testWithoutTheJarTheProjectCannotBeRead() throws Exception {
        ExamplePluginJar.build(extensions, "radio-filter.jar");
        PluginModule.Services withJar = services(PluginJars.load(extensions, getClass().getClassLoader()));
        Filter radio = withJar.filters().get().find(ExamplePluginJar.RADIO_FILTER).orElseThrow().create();
        DataModel project = DataModelFactory.createDataModel("radio");
        project.getFilters().add(radio);
        String yaml = ProjectPersisterUtil.mapperFor(withJar).writeValueAsString(project);

        // a project-file can not cause a plugin to be loaded (projects with missing plugins: phase 5 of #46)
        assertThatThrownBy(() -> ProjectPersisterUtil.mapperFor(services(PluginJars.none()))
                .readValue(yaml, DataModel.class)).isInstanceOf(JacksonException.class)
                .hasMessageContaining(ExamplePluginJar.RADIO_FILTER);
    }
}
