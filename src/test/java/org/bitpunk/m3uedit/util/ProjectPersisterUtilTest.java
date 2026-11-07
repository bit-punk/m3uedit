package org.bitpunk.m3uedit.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.filter.channelname.ChannelNameFilter;
import org.bitpunk.m3uedit.plugins.filter.sdchannel.SdChannelFilter;
import org.bitpunk.m3uedit.plugins.filter.uniquename.UniqueNameFilter;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.input.file.FileInput;
import org.bitpunk.m3uedit.plugins.input.fritzbox.FritzBoxInput;
import org.bitpunk.m3uedit.plugins.input.iptvorg.IptvOrgInput;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.IptvOrgPlaylist;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.PlaylistGrouping;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.output.file.FileOutput;
import org.bitpunk.m3uedit.plugins.output.sftp.SftpOutput;
import org.bitpunk.m3uedit.plugins.output.sftp.model.SftpConnection;
import org.bitpunk.m3uedit.plugins.sorter.Sorter;
import org.bitpunk.m3uedit.plugins.sorter.channelname.ChannelNameSorter;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(dataModel.getSorters()).hasSize(1);
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
        assertThat(loaded.getSortedEntryList()).hasSize(3);
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
    void testLegacyProjectFileWithSftpPasswordCanBeLoaded() throws Exception {
        DataModel dataModel = getDataModel();
        ((SftpOutput) dataModel.getOutputs().get(1)).setSftpConnection(createSftpConnection("ignored"));
        Path legacyFile = Path.of("target/legacy-sftp.m3uproj");
        ProjectPersisterUtil.saveProjectFile(legacyFile.toString(), dataModel);
        // simulate a project file written by an older version, which still contained the password
        String xml = Files.readString(legacyFile);
        assertThat(xml).contains("<userName>user</userName>");
        Files.writeString(legacyFile, xml.replace("<userName>user</userName>",
                "<userName>user</userName><password>oldSecret</password>"));

        SftpConnection connection = ((SftpOutput) ProjectPersisterUtil.loadProject(legacyFile)
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

}