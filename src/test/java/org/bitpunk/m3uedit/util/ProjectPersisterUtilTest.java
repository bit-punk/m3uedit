package org.bitpunk.m3uedit.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.parser.Parser;
import org.bitpunk.m3uedit.plugins.filter.ChannelNameFilter;
import org.bitpunk.m3uedit.plugins.filter.Filter;
import org.bitpunk.m3uedit.plugins.filter.SdChannelFilter;
import org.bitpunk.m3uedit.plugins.filter.UniqueNameFilter;
import org.bitpunk.m3uedit.plugins.input.FileInput;
import org.bitpunk.m3uedit.plugins.input.FritzBoxInput;
import org.bitpunk.m3uedit.plugins.input.Input;
import org.bitpunk.m3uedit.plugins.output.FileOutput;
import org.bitpunk.m3uedit.plugins.output.Output;
import org.bitpunk.m3uedit.plugins.output.SftpOutput;
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