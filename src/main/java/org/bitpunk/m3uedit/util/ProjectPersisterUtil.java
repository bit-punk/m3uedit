package org.bitpunk.m3uedit.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.NullPermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.*;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;
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

    /**
     * Open a file dialog and load a project from the selected file.
     *
     * @return The loaded DataModel object, or null if the project could not be loaded.
     */
    public static DataModel openFileDialogAndLoadProject() {
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
        xStream.ignoreUnknownElements();
        xStream.registerConverter(new ObservableListConverter(xStream.getMapper()));
        // define class-aliases to not expose package-names in the xml-file
        xStream.alias("Project", DataModel.class);
        xStream.alias("Entry", Entry.class);
        xStream.alias("StreamSource", StreamSource.class);

        addAllPluginAliases(xStream);

        // @formatter:off
        xStream.allowTypesByWildcard(new String[]{
                "org.bitpunk.m3uedit.plugins.input.**",
                "org.bitpunk.m3uedit.plugins.output.**",
                "org.bitpunk.m3uedit.plugins.filter.**",
                "org.bitpunk.m3uedit.plugins.sorter.**",
                "org.bitpunk.m3uedit.model.DataModel",
                "org.bitpunk.m3uedit.model.Entry",
                "org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource",
                "com.sun.javafx.collections.ObservableListWrapper",
                "javafx.beans.property.**",
                "java.lang.String",
                "java.net.**",
                "java.util.**"
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

    private static void addAllPluginAliases(XStream xStream) {
        PluginService[] pluginServices = new PluginService[] {
                InputPluginService.getInstance(),
                OutputPluginService.getInstance(),
                FilterPluginService.getInstance(),
                SorterPluginService.getInstance()
        };

        for (PluginService pluginService : pluginServices) {
            addPluginAliases(xStream, pluginService);
        }
    }

    private static void addPluginAliases(XStream xStream, PluginService pluginService) {
        for (var entry : pluginService.getAllPlugins()) {
            xStream.alias(entry.getClass().getSimpleName(), entry.getClass());
        }
    }

}