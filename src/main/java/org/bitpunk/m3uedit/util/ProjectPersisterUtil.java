package org.bitpunk.m3uedit.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.security.NoTypePermission;
import com.thoughtworks.xstream.security.NullPermission;
import com.thoughtworks.xstream.security.PrimitiveTypePermission;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.bitpunk.m3uedit.model.DataModel;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.plugins.*;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.IptvOrgPlaylist;
import org.bitpunk.m3uedit.plugins.input.iptvorg.model.PlaylistGrouping;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;
import org.bitpunk.m3uedit.xstream.BooleanPropertyConverter;
import org.bitpunk.m3uedit.xstream.ObservableListConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedList;

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
            try (Reader reader = Files.newBufferedReader(projectFilePath, StandardCharsets.UTF_8)) {
                dataModel = (DataModel) xstream.fromXML(reader);
            } catch (IOException | XStreamException e) {
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

    // package-private for tests
    static XStream getXStream() {
        XStream xStream = new XStream();
        xStream.addPermission(NoTypePermission.NONE); // forbid everything
        xStream.addPermission(NullPermission.NULL);   // allow "null"
        xStream.addPermission(PrimitiveTypePermission.PRIMITIVES); // allow primitive types
        xStream.autodetectAnnotations(true);
        xStream.ignoreUnknownElements();
        xStream.registerConverter(new ObservableListConverter(xStream.getMapper()));
        // persist only the value of properties like Plugin.enabled, not their listeners and bindings
        xStream.registerConverter(new BooleanPropertyConverter());
        xStream.addDefaultImplementation(SimpleBooleanProperty.class, BooleanProperty.class);
        // define class-aliases to not expose package-names in the xml-file
        xStream.alias("Project", DataModel.class);
        xStream.alias("Entry", Entry.class);
        xStream.alias("StreamSource", StreamSource.class);
        xStream.alias("IptvOrgPlaylist", IptvOrgPlaylist.class);

        addAllPluginAliases(xStream);

        allowProjectTypes(xStream);
        // @formatter:on
        return xStream;
    }

    /**
     * Result of saving a project via the save-dialog.
     */
    public enum SaveResult {
        SAVED, CANCELLED, FAILED
    }

    /**
     * Saves the project to the given file.
     * The xml is created completely before writing, and the file is replaced atomically (where supported),
     * so an existing project-file is not destroyed if saving fails.
     *
     * @return true if the project has been saved
     */
    public static boolean saveProjectFile(String fileName, DataModel dataModel) {
        log.debug("saving projectFile to: {}", fileName);
        Path target = Path.of(fileName).toAbsolutePath();
        Path tempFile = null;
        try {
            String xml = getXStream().toXML(dataModel);
            tempFile = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            Files.writeString(tempFile, xml, StandardCharsets.UTF_8);
            moveReplacing(tempFile, target);
            return true;
        } catch (IOException | XStreamException e) {
            log.error("Error persisting dataModel to file: {}", fileName, e);
            deleteQuietly(tempFile);
            return false;
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("Could not delete temporary file {}", file, e);
            }
        }
    }

    /**
     * Opens a save-dialog and saves the project to the selected file.
     */
    public static SaveResult openSaveDialogAndSaveProject(DataModel dataModel) {
        log.debug("Saving the Project");
        Path saveFilePath = DialogUtil.getSaveFilePath(M3U_PROJECTFILE_DESCRIPTION, M3U_PROJECT_EXTENSION);
        if (saveFilePath == null) {
            return SaveResult.CANCELLED;
        }
        return saveProjectFile(saveFilePath.toString(), dataModel) ? SaveResult.SAVED : SaveResult.FAILED;
    }

    /**
     * Allows exactly the types used in project-files. Everything else is rejected when loading a project-file,
     * as a crafted project-file could otherwise instantiate arbitrary classes ("gadget chains").
     * <p>
     * Maps and sets are not allowed on purpose: with {@link URL}s as keys, their hashCode would trigger
     * a DNS lookup while loading.
     */
    private static void allowProjectTypes(XStream xStream) {
        // all plugins, including the ones provided via ServiceLoader
        xStream.allowTypeHierarchy(Plugin.class);
        // @formatter:off
        xStream.allowTypes(new Class[]{
                DataModel.class,
                StreamSource.class,
                FritzBoxConnection.class,
                IptvOrgPlaylist.class,
                PlaylistGrouping.class,
                SftpConnection.class,
                // legacy format of Plugin.enabled, see BooleanPropertyConverter
                SimpleBooleanProperty.class,
                String.class,
                URL.class,
                URI.class,
                ArrayList.class,
                LinkedList.class
        });
        xStream.allowTypes(new String[]{
                "com.sun.javafx.collections.ObservableListWrapper",
                "java.util.Collections$EmptyList",
                "java.util.Collections$SingletonList",
                "java.util.Collections$UnmodifiableList",
                "java.util.Collections$UnmodifiableRandomAccessList",
                "java.util.ImmutableCollections$List12",
                "java.util.ImmutableCollections$ListN"
        });
        // @formatter:on
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