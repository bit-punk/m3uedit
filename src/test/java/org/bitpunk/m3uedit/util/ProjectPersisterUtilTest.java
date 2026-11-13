package org.bitpunk.m3uedit.util;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.ManualChange;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.filter.channelname.ChannelNameFilter;
import org.bitpunk.m3uedit.plugins.filter.sdchannel.SdChannelFilter;
import org.bitpunk.m3uedit.plugins.filter.uniquename.UniqueNameFilter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.file.FileInput;
import org.bitpunk.m3uedit.plugins.FilterPluginService;
import org.bitpunk.m3uedit.plugins.input.fritzbox.FritzBoxInput;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;
import org.bitpunk.m3uedit.plugins.input.iptvorg.IptvOrgInput;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.IptvOrgPlaylist;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.PlaylistGrouping;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.input.url.UrlInput;
import org.bitpunk.m3uedit.plugins.output.file.FileOutput;
import org.bitpunk.m3uedit.plugins.input.sftp.SftpInput;
import org.bitpunk.m3uedit.plugins.output.sftp.SftpOutput;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.bitpunk.m3uedit.plugins.sorter.channelname.ChannelNameSorter;
import org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder.PreferredChannelOrderSorter;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ProjectPersisterUtilTest {

    @Test
    void testWriteProjectFile() {
        DataModel dataModel = getDataModel();
        String projectFileName = "target/testproject.m3uproj";
        ProjectPersisterUtil.saveProjectFile(projectFileName, dataModel);
        assertThat(Files.exists(Path.of(projectFileName))).isTrue();
    }

    @Test
    void testReadProjectFile() {
        String projectFileName = "src/test/resources/project/testproject.m3uproj";
        DataModel dataModel = ProjectPersisterUtil.loadProject(Path.of(projectFileName));
        assertThat(dataModel).isNotNull();
        assertThat(dataModel.getName()).isEqualTo("Kurt");
        assertThat(dataModel.getInputs()).hasExactlyElementsOfTypes(FileInput.class, FritzBoxInput.class);
        assertThat(dataModel.getInputs()).extracting(input -> input.getEnabled().get()).containsExactly(true, false);
        FritzBoxInput fritzBoxInput = (FritzBoxInput) dataModel.getInputs().get(1);
        assertThat(fritzBoxInput.getSelectedStreamSources()).containsExactly(
                new StreamSource("tvsd.m3u", "SD streams"), new StreamSource("tvhd.m3u", "HD streams"));
        assertThat(fritzBoxInput.getFritzBoxConnection().getBaseUrl()).isEqualTo("http://fritz.box/dvb/m3u/");
        assertThat(dataModel.getFilters())
                .hasExactlyElementsOfTypes(ChannelNameFilter.class, UniqueNameFilter.class, SdChannelFilter.class);
        assertThat(dataModel.getFilters()).extracting(filter -> filter.getEnabled().get())
                .containsExactly(false, true, false);
        assertThat(dataModel.getSorters()).hasExactlyElementsOfTypes(ChannelNameSorter.class);
        assertThat(((FileOutput) dataModel.getOutputs().getFirst()).getOutput())
                .isEqualTo(URI.create("file:///tmp/playlist.m3u"));
    }

    @Test
    void testWriteAndReadBackProjectFile() {
        DataModel original = getDataModel();
        String projectFileName = "target/testproject.m3uproj";
        ProjectPersisterUtil.saveProjectFile(projectFileName, original);
        DataModel loaded = ProjectPersisterUtil.loadProject(Path.of(projectFileName));
        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo("test");
        assertThat(loaded.getInputs()).hasSize(3);
        assertThat(loaded.getInputs().get(2)).isInstanceOf(IptvOrgInput.class);
        assertThat(((IptvOrgInput) loaded.getInputs().get(2)).getSelectedPlaylists())
                .containsExactly(new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany"));
        assertThat(loaded.getInputs().get(2).getSource()).isEqualTo("countries/de.m3u");
        assertThat(loaded.getFilters()).hasSize(3);
        assertThat(loaded.getSorters()).hasSize(1);
        assertThat(loaded.getOutputs()).hasSize(2);
        // entries are derived data, they are re-loaded from the inputs instead of being persisted
        assertThat(loaded.getRawEntryList()).isEmpty();
        assertThat(loaded.getFilteredEntryList()).isEmpty();
        assertThat(loaded.getSortedEntryList()).isEmpty();
    }

    @Test
    void testEpgUrlsArePersisted() {
        DataModel original = getDataModel();
        original.getEpgUrls().setAll("https://epg.example.org/de.xml.gz", "https://epg.example.org/at.xml");
        String projectFileName = "target/testproject-epg.m3uproj";

        ProjectPersisterUtil.saveProjectFile(projectFileName, original);
        DataModel loaded = ProjectPersisterUtil.loadProject(Path.of(projectFileName));

        assertThat(loaded.getEpgUrls()).containsExactly("https://epg.example.org/de.xml.gz", "https://epg.example.org/at.xml");
    }

    @Test
    void testEntriesAreNotPersisted() throws Exception {
        DataModel dataModel = getDataModel();
        dataModel.getRawEntryList().addAll(getEntries());
        dataModel.getFilteredEntryList().addAll(getEntries());
        String projectFileName = "target/testproject-without-entries.m3uproj";

        ProjectPersisterUtil.saveProjectFile(projectFileName, dataModel);

        String yaml = Files.readString(Path.of(projectFileName));
        assertThat(yaml).doesNotContain("EntryList").doesNotContain("tvgName").contains("inputs:");
    }

    @Test
    void testOnlyTheProjectConfigurationIsPersisted() throws Exception {
        DataModel dataModel = getDataModel();
        Path projectFile = Path.of("target/testproject-content.m3uproj");

        ProjectPersisterUtil.saveProjectFile(projectFile.toString(), dataModel);

        String yaml = Files.readString(projectFile);
        // plugins are stored by their simple class-name, without package
        assertThat(yaml).contains("type: FileInput").contains("type: ChannelNameSorter")
                .doesNotContain("org.bitpunk");
        // neither the constant name and description of the plugins, nor the state of the editor
        assertThat(yaml).doesNotContain("File Input").doesNotContain("description")
                .doesNotContain("changed").doesNotContain("busy");
    }

    @Test
    void testSftpPasswordIsNotPersisted() throws Exception {
        DataModel dataModel = getDataModel();
        SftpOutput sftpOutput = (SftpOutput) dataModel.getOutputs().get(1);
        sftpOutput.setSftpConnection(createSftpConnection("topSecret42"));

        String projectFileName = "target/testproject-sftp.m3uproj";
        ProjectPersisterUtil.saveProjectFile(projectFileName, dataModel);
        assertThat(Files.readString(Path.of(projectFileName))).doesNotContain("topSecret42").contains("example.com");

        SftpConnection loaded = ((SftpOutput) ProjectPersisterUtil.loadProject(Path.of(projectFileName))
                .getOutputs().get(1)).getSftpConnection();
        assertThat(loaded.getHostName()).isEqualTo("example.com");
        assertThat(loaded.getPassword()).isNull();
    }

    @Test
    void testSftpPasswordInProjectFileIsIgnored() throws Exception {
        DataModel dataModel = getDataModel();
        ((SftpOutput) dataModel.getOutputs().get(1)).setSftpConnection(createSftpConnection("ignored"));
        Path projectFile = Path.of("target/testproject-sftp-password.m3uproj");
        ProjectPersisterUtil.saveProjectFile(projectFile.toString(), dataModel);
        // a password added to the project-file manually is not used
        modifyProjectFile(projectFile, project ->
                ((ObjectNode) project.findParent("userName")).put("password", "oldSecret"));
        assertThat(Files.readString(projectFile)).contains("oldSecret");

        SftpConnection connection = ((SftpOutput) ProjectPersisterUtil.loadProject(projectFile)
                .getOutputs().get(1)).getSftpConnection();
        assertThat(connection.getHostName()).isEqualTo("example.com");
        assertThat(connection.getPassword()).isNull();
    }

    @Test
    void testSftpOverwriteFlagIsPersisted() {
        DataModel dataModel = getDataModel();
        SftpConnection connection = createSftpConnection("ignored");
        connection.setOverwriteWithoutAsking(true);
        ((SftpOutput) dataModel.getOutputs().get(1)).setSftpConnection(connection);

        String projectFileName = "target/testproject-sftp-overwrite.m3uproj";
        ProjectPersisterUtil.saveProjectFile(projectFileName, dataModel);
        SftpConnection loaded = ((SftpOutput) ProjectPersisterUtil.loadProject(Path.of(projectFileName))
                .getOutputs().get(1)).getSftpConnection();

        assertThat(loaded.isOverwriteWithoutAsking()).isTrue();
    }

    @Test
    void testSftpInputIsPersistedWithoutPassword() throws Exception {
        DataModel dataModel = getDataModel();
        SftpConnection connection = createSftpConnection("topSecret42");
        connection.setPath("/playlists");
        connection.setFileName("tv.m3u");
        SftpInput sftpInput = new SftpInput();
        sftpInput.setSftpConnection(connection);
        dataModel.getInputs().add(sftpInput);

        String projectFileName = "target/testproject-sftp-input.m3uproj";
        ProjectPersisterUtil.saveProjectFile(projectFileName, dataModel);
        assertThat(Files.readString(Path.of(projectFileName))).contains("type: SftpInput").doesNotContain("topSecret42");

        DataModel loaded = ProjectPersisterUtil.loadProject(Path.of(projectFileName));
        SftpInput loadedInput = (SftpInput) loaded.getInputs().get(3);
        assertThat(loadedInput.getSource()).isEqualTo("sftp://user@example.com:22/playlists/tv.m3u");
        assertThat(loadedInput.getSftpConnection().getPassword()).isNull();
    }

    private static SftpConnection createSftpConnection(String password) {
        SftpConnection connection = new SftpConnection();
        connection.setHostName("example.com");
        connection.setPort(22);
        connection.setUserName("user");
        connection.setPassword(password);
        connection.setPath("/playlists/tv.m3u");
        return connection;
    }

    private static DataModel getDataModel() {
        DataModel dataModel = new DataModel("test");
        dataModel.setFilters(getFilters());
        dataModel.setSorters(getSorters());
        dataModel.setInputs(getInputs());
        dataModel.setOutputs(getOutputs());
        dataModel.setSortedEntryList(getEntries());
        return dataModel;
    }

    private static ObservableList<Sorter> getSorters() {
        List<Sorter> filterList = new ArrayList<>();
        filterList.add(new ChannelNameSorter());
        return FXCollections.observableArrayList(filterList);
    }
    private static ObservableList<Filter> getFilters() {
        List<Filter> filterList = new ArrayList<>();
        filterList.add(new ChannelNameFilter());
        filterList.add(new UniqueNameFilter());
        filterList.add(new SdChannelFilter());
        return FXCollections.observableArrayList(filterList);
    }

    private static ObservableList<Input> getInputs() {
        List<Input> inputList = new ArrayList<>();
        inputList.add(new FileInput());
        inputList.add(new FritzBoxInput());
        IptvOrgInput iptvOrgInput = new IptvOrgInput();
        iptvOrgInput.setSelectedPlaylists(new ArrayList<>(List.of(new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany"))));
        inputList.add(iptvOrgInput);
        return FXCollections.observableArrayList(inputList);
    }
    private static ObservableList<Output> getOutputs() {
        List<Output> outputList = new ArrayList<>();
        outputList.add(new FileOutput());
        outputList.add(new SftpOutput());
        return FXCollections.observableArrayList(outputList);
    }

    private static ObservableList<Entry> getEntries() {
        List<Entry> entryList = new Parser().parse(ProjectPersisterUtilTest.class.getResourceAsStream("/test.m3u"));
        return FXCollections.observableArrayList(entryList);
    }


    @Test
    void testProjectWithBoundEnabledPropertiesCanBeSavedAndLoaded() throws Exception {
        DataModel dataModel = getDataModel();
        Filter enabledFilter = dataModel.getFilters().get(1);
        Filter disabledFilter = dataModel.getFilters().get(2);
        enabledFilter.getEnabled().set(true);
        // simulates the UI: checkbox-cells bind bidirectionally (using weak references) and add listeners
        for (Filter filter : dataModel.getFilters()) {
            new SimpleBooleanProperty().bindBidirectional(filter.getEnabled());
            filter.getEnabled().addListener((observable, oldValue, newValue) -> dataModel.filterRawEntries());
        }
        dataModel.getSorters().getFirst().getEnabled().set(true);
        new SimpleBooleanProperty().bindBidirectional(dataModel.getSorters().getFirst().getEnabled());
        Path projectFile = Path.of("target/testproject-bound-properties.m3uproj");

        ProjectPersisterUtil.saveProjectFile(projectFile.toString(), dataModel);

        String yaml = Files.readString(projectFile);
        assertThat(yaml).contains("enabled: true").contains("enabled: false")
                .doesNotContain("SimpleBooleanProperty").doesNotContain("WeakReference");
        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getFilters().get(1).getEnabled().get()).isTrue();
        assertThat(loaded.getFilters().get(2).getEnabled().get()).isFalse();
        assertThat(loaded.getSorters().getFirst().getEnabled().get()).isTrue();
        assertThat(enabledFilter).isNotSameAs(loaded.getFilters().get(1));
        assertThat(disabledFilter.getEnabled().get()).isFalse();
    }

    @Test
    void testMissingEnabledPropertyDefaultsToDisabled() throws Exception {
        DataModel dataModel = getDataModel();
        Path projectFile = Path.of("target/testproject-without-enabled.m3uproj");
        ProjectPersisterUtil.saveProjectFile(projectFile.toString(), dataModel);
        modifyProjectFile(projectFile, project -> project.findParents("enabled")
                .forEach(plugin -> ((ObjectNode) plugin).remove("enabled")));
        assertThat(Files.readString(projectFile)).doesNotContain("enabled");

        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getFilters()).allSatisfy(filter -> assertThat(filter.getEnabled()).isNotNull()
                .extracting(property -> property.get()).isEqualTo(false));
    }

    @Test
    void testSaveReturnsTrueAndLeavesNoTemporaryFiles(@TempDir Path tempDir) throws Exception {
        Path projectFile = tempDir.resolve("project.m3uproj");

        assertThat(ProjectPersisterUtil.saveProjectFile(projectFile.toString(), getDataModel())).isTrue();

        assertThat(projectFile).exists();
        try (var files = Files.list(tempDir)) {
            assertThat(files).containsExactly(projectFile);
        }
    }

    @Test
    void testFailedSaveKeepsExistingProjectFile(@TempDir Path tempDir) throws Exception {
        Path projectFile = tempDir.resolve("project.m3uproj");
        Files.writeString(projectFile, "existing project");
        DataModel dataModel = getDataModel();
        // a cyclic reference cannot be serialized
        dataModel.getFilters().add(new UniqueNameFilter() {
            @SuppressWarnings("unused")
            private final Object self = this;
        });

        assertThat(ProjectPersisterUtil.saveProjectFile(projectFile.toString(), dataModel)).isFalse();

        assertThat(projectFile).hasContent("existing project");
        try (var files = Files.list(tempDir)) {
            assertThat(files).containsExactly(projectFile);
        }
    }

    @Test
    void testLoadingCorruptProjectFileReturnsNull(@TempDir Path tempDir) throws Exception {
        Path projectFile = Files.writeString(tempDir.resolve("corrupt.m3uproj"), "name: x\ninputs: [");
        assertThat(ProjectPersisterUtil.loadProject(projectFile)).isNull();
    }

    @Test
    void testProjectWithAllPluginsConfiguredCanBeSavedAndLoaded(@TempDir Path tempDir) throws Exception {
        DataModel dataModel = new DataModel("all plugins");
        FileInput fileInput = new FileInput();
        fileInput.setSource("/tmp/playlist.m3u");
        FritzBoxInput fritzBoxInput = new FritzBoxInput();
        FritzBoxConnection fritzBoxConnection = new FritzBoxConnection();
        fritzBoxConnection.setBaseUrl("http://192.168.178.1/dvb/m3u/");
        fritzBoxConnection.getSelectedStreamSources().add(new StreamSource("tvhd.m3u", "HD streams"));
        fritzBoxInput.setFritzBoxConnection(fritzBoxConnection);
        fritzBoxInput.setSelectedStreamSources(fritzBoxConnection.getSelectedStreamSources());
        UrlInput urlInput = new UrlInput();
        List<URL> urls = new ArrayList<>(List.of(URI.create("http://example.com/a.m3u").toURL()));
        urlInput.setUrlList(urls);
        urlInput.setSelectedInputUrls(urls);
        IptvOrgInput iptvOrgInput = new IptvOrgInput();
        iptvOrgInput.setSelectedPlaylists(new ArrayList<>(List.of(
                new IptvOrgPlaylist(PlaylistGrouping.COUNTRY, "DE", "Germany"),
                new IptvOrgPlaylist(PlaylistGrouping.CHANNEL, "ZDF.de", "ZDF"))));
        SftpInput sftpInput = new SftpInput();
        sftpInput.setSftpConnection(createSftpConnection("secret"));
        SftpOutput sftpOutput = new SftpOutput();
        sftpOutput.setSftpConnection(createSftpConnection("secret"));
        PreferredChannelOrderSorter preferredSorter = new PreferredChannelOrderSorter();
        preferredSorter.setSelectedProfileId("my_profile");
        preferredSorter.getEnabled().set(true);

        dataModel.getInputs().addAll(fileInput, fritzBoxInput, urlInput, iptvOrgInput, sftpInput);
        dataModel.getFilters().addAll(FilterPluginService.getInstance().getAllPlugins());
        dataModel.getSorters().addAll(new ChannelNameSorter(), preferredSorter);
        dataModel.getOutputs().addAll(new FileOutput(), sftpOutput);
        Path projectFile = tempDir.resolve("all.m3uproj");

        assertThat(ProjectPersisterUtil.saveProjectFile(projectFile.toString(), dataModel)).isTrue();
        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);

        assertThat(loaded).isNotNull();
        assertThat(((FileInput) loaded.getInputs().get(0)).getSource()).isEqualTo("/tmp/playlist.m3u");
        FritzBoxInput loadedFritzBox = (FritzBoxInput) loaded.getInputs().get(1);
        assertThat(loadedFritzBox.getFritzBoxConnection().getBaseUrl()).isEqualTo("http://192.168.178.1/dvb/m3u/");
        assertThat(loadedFritzBox.getSelectedStreamSources()).containsExactly(new StreamSource("tvhd.m3u", "HD streams"));
        assertThat(((UrlInput) loaded.getInputs().get(2)).getSelectedInputUrls())
                .containsExactly(URI.create("http://example.com/a.m3u").toURL());
        assertThat(((IptvOrgInput) loaded.getInputs().get(3)).getSelectedPlaylists()).hasSize(2);
        assertThat(((SftpInput) loaded.getInputs().get(4)).getSftpConnection().getHostName()).isEqualTo("example.com");
        assertThat(loaded.getFilters()).hasSameSizeAs(dataModel.getFilters());
        assertThat(((PreferredChannelOrderSorter) loaded.getSorters().get(1)).getSelectedProfileId()).isEqualTo("my_profile");
        assertThat(loaded.getSorters().get(1).getEnabled().get()).isTrue();
        assertThat(((SftpOutput) loaded.getOutputs().get(1)).getSftpConnection().getHostName()).isEqualTo("example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // arbitrary classes as plugins
            "inputs: [{type: java.lang.ProcessBuilder, command: [touch]}]",
            "inputs: [{type: ProcessBuilder, command: [touch]}]",
            "inputs: [!<java.lang.ProcessBuilder> {command: [touch]}]",
            // plugins of another kind, or without type
            "inputs: [{type: UniqueNameFilter}]",
            "outputs: [{type: FileInput}]",
            "inputs: [{source: /tmp/a.m3u}]",
            // arbitrary classes as field-values
            "inputs: [{type: FileInput, source: {type: java.lang.Runtime}}]",
            "inputs: [{type: FileInput, enabled: {type: javafx.beans.property.SimpleObjectProperty}}]"
    })
    void testProjectFilesWithUnknownTypesAreRejected(String content, @TempDir Path tempDir) throws Exception {
        String yaml = "name: crafted\n" + content + "\n";

        // rejected because of the types, not because of malformed yaml
        assertThatThrownBy(() -> ProjectPersisterUtil.getMapper().readValue(yaml, DataModel.class))
                .isInstanceOf(JacksonException.class)
                .hasMessageNotContaining("Unexpected end-of-input");
        Path projectFile = Files.writeString(tempDir.resolve("crafted.m3uproj"), yaml);
        assertThat(ProjectPersisterUtil.loadProject(projectFile)).isNull();
    }

    @Test
    void testValidMinimalProjectFileIsAccepted(@TempDir Path tempDir) throws Exception {
        Path projectFile = Files.writeString(tempDir.resolve("minimal.m3uproj"), """
                name: minimal
                inputs:
                  - type: FileInput
                    enabled: true
                    source: /tmp/a.m3u
                """);

        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);

        assertThat(loaded).isNotNull();
        assertThat(((FileInput) loaded.getInputs().getFirst()).getSource()).isEqualTo("/tmp/a.m3u");
        assertThat(loaded.getInputs().getFirst().getEnabled().get()).isTrue();
        assertThat(loaded.getFilters()).isEmpty();
        assertThat(loaded.getOutputs()).isEmpty();
    }

    @Test
    void testLoadedProjectRemembersItsFileAndIsSavedThereAgain(@TempDir Path tempDir) throws Exception {
        Path projectFile = tempDir.resolve("remember.m3uproj");
        assertThat(ProjectPersisterUtil.saveProjectFile(projectFile.toString(), getDataModel())).isTrue();

        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);
        assertThat(loaded.getProjectFile()).isEqualTo(projectFile.toAbsolutePath());

        loaded.setName("renamed");
        assertThat(ProjectPersisterUtil.saveProject(loaded)).isEqualTo(ProjectPersisterUtil.SaveResult.SAVED);
        assertThat(Files.readString(projectFile)).contains("name: renamed");
        // the project-file itself is not part of its content
        assertThat(Files.readString(projectFile)).doesNotContain("projectFile");
    }

    @Test
    void testFailingSaveReportsFailed(@TempDir Path tempDir) {
        DataModel dataModel = getDataModel();
        dataModel.setProjectFile(tempDir.resolve("missing-directory").resolve("project.m3uproj"));

        assertThat(ProjectPersisterUtil.saveProject(dataModel)).isEqualTo(ProjectPersisterUtil.SaveResult.FAILED);
    }

    private static void modifyProjectFile(Path projectFile, Consumer<JsonNode> modification) throws Exception {
        JsonNode project = ProjectPersisterUtil.getMapper().readTree(projectFile.toFile());
        modification.accept(project);
        Files.writeString(projectFile, ProjectPersisterUtil.getMapper().writeValueAsString(project));
    }

    @ParameterizedTest
    @CsvSource({
            "/tmp/project, /tmp/project.m3uproj",
            "/tmp/project.m3uproj, /tmp/project.m3uproj",
            "/tmp/project.M3UPROJ, /tmp/project.M3UPROJ",
            "/tmp/project.m3u, /tmp/project.m3u.m3uproj",
            "/tmp/my.project, /tmp/my.project.m3uproj",
            "project, project.m3uproj"
    })
    void testProjectFileSuffixIsAppendedIfMissing(String selected, String expected) {
        assertThat(ProjectPersisterUtil.withProjectFileSuffix(Path.of(selected))).isEqualTo(Path.of(expected));
    }

    @Test
    void testSaveAsAppendsSuffix(@TempDir Path tempDir) {
        DataModel dataModel = getDataModel();
        List<Path> confirmations = new ArrayList<>();

        ProjectPersisterUtil.SaveResult result = ProjectPersisterUtil.saveProjectAs(dataModel, tempDir.resolve("project"),
                file -> confirmations.add(file));

        assertThat(result).isEqualTo(ProjectPersisterUtil.SaveResult.SAVED);
        assertThat(tempDir.resolve("project.m3uproj")).exists();
        assertThat(tempDir.resolve("project")).doesNotExist();
        assertThat(dataModel.getProjectFile()).isEqualTo(tempDir.resolve("project.m3uproj").toAbsolutePath());
        // nothing to replace, so no need to ask
        assertThat(confirmations).isEmpty();
    }

    @Test
    void testSaveAsReplacesExistingFileWithSuffixIfConfirmed(@TempDir Path tempDir) throws Exception {
        Path existing = Files.writeString(tempDir.resolve("project.m3uproj"), "existing project");
        List<Path> confirmations = new ArrayList<>();

        ProjectPersisterUtil.SaveResult result = ProjectPersisterUtil.saveProjectAs(getDataModel(), tempDir.resolve("project"),
                file -> confirmations.add(file));

        assertThat(result).isEqualTo(ProjectPersisterUtil.SaveResult.SAVED);
        assertThat(confirmations).containsExactly(existing);
        assertThat(Files.readString(existing)).contains("name: test");
    }

    @Test
    void testSaveAsKeepsExistingFileWithSuffixIfNotConfirmed(@TempDir Path tempDir) throws Exception {
        Path existing = Files.writeString(tempDir.resolve("project.m3uproj"), "existing project");
        DataModel dataModel = getDataModel();

        ProjectPersisterUtil.SaveResult result = ProjectPersisterUtil.saveProjectAs(dataModel, tempDir.resolve("project"),
                file -> false);

        assertThat(result).isEqualTo(ProjectPersisterUtil.SaveResult.CANCELLED);
        assertThat(existing).hasContent("existing project");
        assertThat(dataModel.getProjectFile()).isNull();
    }

    @Test
    void testSaveAsDoesNotAskAgainForFileSelectedWithSuffix(@TempDir Path tempDir) throws Exception {
        // the save-dialog itself already asked before replacing the file selected
        Path existing = Files.writeString(tempDir.resolve("project.m3uproj"), "existing project");

        ProjectPersisterUtil.SaveResult result = ProjectPersisterUtil.saveProjectAs(getDataModel(), existing,
                file -> { throw new AssertionError("must not ask"); });

        assertThat(result).isEqualTo(ProjectPersisterUtil.SaveResult.SAVED);
        assertThat(Files.readString(existing)).contains("name: test");
    }

    @Test
    void testSaveAsWithCancelledDialog() {
        assertThat(ProjectPersisterUtil.saveProjectAs(getDataModel(), null, file -> true))
                .isEqualTo(ProjectPersisterUtil.SaveResult.CANCELLED);
    }

    @Test
    void testManualChangesArePersisted(@TempDir Path tempDir) throws Exception {
        DataModel dataModel = new DataModel("manual");
        Entry a = new Entry();
        a.setName("a");
        a.setChannelUri("http://example.com/a");
        Entry b = new Entry();
        b.setName("b");
        b.setChannelUri("http://example.com/b");
        dataModel.getSortedEntryList().setAll(a, b);
        Entry added = new Entry();
        added.setName("added");
        added.setChannelUri("http://example.com/added");
        added.setGroupTitle("Favorites");
        added.setIndex(7);
        dataModel.addEntry(added);
        dataModel.moveEntries(List.of(b), 0);
        dataModel.removeEntries(List.of(a));
        Path projectFile = tempDir.resolve("manual.m3uproj");

        assertThat(ProjectPersisterUtil.saveProjectFile(projectFile.toString(), dataModel)).isTrue();

        String yaml = Files.readString(projectFile);
        assertThat(yaml).contains("manualChanges:").contains("action: ADD").contains("action: MOVE")
                .contains("action: REMOVE")
                // the position shown in the playlist is not persisted
                .doesNotContain("index");
        DataModel loaded = ProjectPersisterUtil.loadProject(projectFile);
        // entries are compared by identity, so the other values are compared
        assertThat(loaded.getManualChanges())
                .extracting(ManualChange::action, ManualChange::target, ManualChange::after)
                .containsExactlyElementsOf(dataModel.getManualChanges().stream()
                        .map(change -> tuple(change.action(), change.target(), change.after()))
                        .toList());
        Entry loadedAdded = loaded.getManualChanges().getFirst().entry();
        assertThat(loadedAdded.getName()).isEqualTo("added");
        assertThat(loadedAdded.getGroupTitle()).isEqualTo("Favorites");
        // re-applied to the playlist computed from the inputs
        assertThat(ManualChange.applyAll(List.of(a, b), loaded.getManualChanges()))
                .extracting(Entry::getName).containsExactly("b", "added");
    }
}
