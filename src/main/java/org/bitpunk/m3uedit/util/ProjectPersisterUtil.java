package org.bitpunk.m3uedit.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.NullPermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.FilterPluginService;
import org.bitpunk.m3uedit.plugins.InputPluginService;
import org.bitpunk.m3uedit.plugins.OutputPluginService;
import org.bitpunk.m3uedit.plugins.SorterPluginService;
import org.bitpunk.m3uedit.xstream.ObservableListConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.apache.commons.lang3.StringUtils.isEmpty;

public class ProjectPersisterUtil {
    public static final String M3U_PROJECTFILE_DESCRIPTION = "M3UProjectfile (*.m3uproj)";
    public static final String M3U_PROJECT_EXTENSION = "*.m3uproj";
    private static final Logger log = LoggerFactory.getLogger(ProjectPersisterUtil.class);

    private ProjectPersisterUtil() {
    }

    public static DataModel openFileDialogAndLoadProject() throws IOException {
        Path projectFilePath = DialogUtil.getOpenFilePath(M3U_PROJECTFILE_DESCRIPTION, M3U_PROJECT_EXTENSION);
        return loadProject(projectFilePath);
    }

    public static DataModel loadProject(Path projectFilePath) {
        DataModel dataModel = null;
        if (projectFilePath != null) {
            XStream xstream = getXStream();
            try (FileReader reader = new FileReader(projectFilePath.toFile())) {
                dataModel = (DataModel) xstream.fromXML(reader);
            } catch (IOException e) {
                log.error("Exception trying to load project-file: {}", projectFilePath, e);
            }
        }
        if (dataModel == null) {
            log.error("DataModel loaded from xml-file was null");
            return null;
        }
        if (isEmpty(dataModel.getName())) {
            log.error("DataModel loaded has an empty name: {}", dataModel);
            return null;
        }
        return dataModel;
    }

    private static XStream getXStream() {
        XStream xStream = new XStream();
        xStream.addPermission(NoTypePermission.NONE); // forbid everything
        xStream.addPermission(NullPermission.NULL);   // allow "null"
        xStream.addPermission(PrimitiveTypePermission.PRIMITIVES); // allow primitive types
        xStream.autodetectAnnotations(true);
        xStream.registerConverter(new ObservableListConverter(xStream.getMapper()));
        // define class-aliases to not expose package-names in the xml-file
        xStream.alias("Project", DataModel.class);
        addPluginAliases(xStream);
        xStream.alias("Entry", Entry.class);
        addOutputPluginAliases(xStream);
        // @formatter:off
        xStream.allowTypesByWildcard(new String[]{
                "org.bitpunk.m3uedit.plugins.input.*",
                "org.bitpunk.m3uedit.plugins.output.*",
                "org.bitpunk.m3uedit.plugins.filter.*",
                "org.bitpunk.m3uedit.plugins.sorter.*",
                "org.bitpunk.m3uedit.model.DataModel",
                "org.bitpunk.m3uedit.model.Entry",
                "com.sun.javafx.collections.ObservableListWrapper",
                "java.lang.String"
        });
        // @formatter:on
        return xStream;
    }

    public static void saveProjectFile(String fileName, DataModel dataModel) {
        log.debug("saving projectFile to: {}", fileName);
        XStream xstream = getXStream();
        try (FileWriter writer = new FileWriter(fileName)) {
            xstream.toXML(dataModel, writer);
        } catch (IOException e) {
            log.error("Error persisting dataModel to file: {}", fileName, e);
        }
    }

    public static void openSaveDialogAndSaveProject(DataModel dataModel) {
        log.debug("Saving the Project");
        Path saveFilePath = DialogUtil.getSaveFilePath(M3U_PROJECTFILE_DESCRIPTION, M3U_PROJECT_EXTENSION);
        if (saveFilePath != null) {
            ProjectPersisterUtil.saveProjectFile(saveFilePath.toString(), dataModel);
        }
    }

    private static void addPluginAliases(XStream xStream) {
        addInputPluginAliases(xStream);
        addFilterPluginAliases(xStream);
        addSorterPluginAliases(xStream);
    }
    private static void addInputPluginAliases(XStream xStream) {
        InputPluginService inputPluginService = InputPluginService.getInstance();
        for (var entry : inputPluginService.getAllPlugins()) {
            xStream.alias(entry.getClass().getSimpleName(), entry.getClass());
        }
    }

    private static void addFilterPluginAliases(XStream xStream) {
        FilterPluginService filterPluginService = FilterPluginService.getInstance();
        for (var entry : filterPluginService.getAllPlugins()) {
            xStream.alias(entry.getClass().getSimpleName(), entry.getClass());
        }
    }

    private static void addSorterPluginAliases(XStream xStream) {
        SorterPluginService sorterPluginService = SorterPluginService.getInstance();
        for (var entry : sorterPluginService.getAllPlugins()) {
            xStream.alias(entry.getClass().getSimpleName(), entry.getClass());
        }
    }

    private static void addOutputPluginAliases(XStream xStream) {
        OutputPluginService outputPluginService = OutputPluginService.getInstance();
        for (var entry : outputPluginService.getAllPlugins()) {
            xStream.alias(entry.getClass().getSimpleName(), entry.getClass());
        }
    }

}