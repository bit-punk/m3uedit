package org.bitpunk.m3uedit.util;

import javafx.collections.FXCollections;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Project;

import java.util.ArrayList;

public class DataModelMapper {
    private DataModelMapper() {
    }

    public static Project mapToProject(DataModel dataModel) {
        Project project = new Project();
        if (dataModel != null) {
            project.setName(dataModel.getName());
            project.setSorters(new ArrayList<>(dataModel.getSorters()));
            project.setFilters(new ArrayList<>(dataModel.getFilters()));
            project.setInputs(new ArrayList<>(dataModel.getInputs()));
            project.setOutputs(new ArrayList<>(dataModel.getOutputs()));
            project.setEntries(new ArrayList<>(dataModel.getEntries()));
        }
        return project;
    }

    public static DataModel mapToDataModel(Project project) {
        DataModel dataModel = new DataModel();
        dataModel.setName(project.getName());
        dataModel.setSorters(FXCollections.observableArrayList(project.getSorters()));
        dataModel.setFilters(FXCollections.observableArrayList(project.getFilters()));
        dataModel.setInputs(FXCollections.observableArrayList(project.getInputs()));
        dataModel.setOutputs(FXCollections.observableArrayList(project.getOutputs()));
        dataModel.setEntries(FXCollections.observableArrayList(project.getEntries()));
        return dataModel;
    }
}