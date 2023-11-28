package org.bitpunk.m3uedit.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.NullPermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.filter.ChannelNameFilter;
import org.bitpunk.m3uedit.plugins.filter.SdChannelFilter;
import org.bitpunk.m3uedit.plugins.filter.UniqueNameFilter;
import org.bitpunk.m3uedit.plugins.input.FileInput;
import org.bitpunk.m3uedit.plugins.input.FritzBoxInput;
import org.bitpunk.m3uedit.plugins.output.FileOutput;
import org.bitpunk.m3uedit.plugins.output.SftpOutput;
import org.bitpunk.m3uedit.plugins.sorter.ChannelNameSorter;
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
        XStream xstream = new XStream();
        xstream.addPermission(NoTypePermission.NONE); //forbid everything
        xstream.addPermission(NullPermission.NULL);   // allow "null"
        xstream.addPermission(PrimitiveTypePermission.PRIMITIVES); // allow primitive types
        xstream.autodetectAnnotations(true);
        xstream.registerConverter(new ObservableListConverter(xstream.getMapper()));
        // define class-aliases to not expose package-names in the xml-file
        // ToDo can i set class-aliases dynamically?
        xstream.alias("Project", DataModel.class);
        xstream.alias("FileInput", FileInput.class);
        xstream.alias("FritzBoxInput", FritzBoxInput.class);
        xstream.alias("ChannelNameFilter", ChannelNameFilter.class);
        xstream.alias("UniqueNameFilter", UniqueNameFilter.class);
        xstream.alias("SdChannelFilter", SdChannelFilter.class);
        xstream.alias("ChannelNameSorter", ChannelNameSorter.class);
        xstream.alias("Entry", Entry.class);
        xstream.alias("FileOutput", FileOutput.class);
        xstream.alias("SftpOutput", SftpOutput.class);
        // @formatter:off
        xstream.allowTypesByWildcard(new String[]{
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
        return xstream;
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

}