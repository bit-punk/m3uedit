package org.bitpunk.m3uedit.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Plugin;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.jackson.JavaFxModule;
import org.bitpunk.m3uedit.jackson.PluginModule;
import org.bitpunk.m3uedit.model.DataModel;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.Predicate;

import static org.apache.commons.lang3.StringUtils.isEmpty;

@Slf4j
public class ProjectPersisterUtil {
    public static final String M3U_PROJECT_FILE_SUFFIX = ".m3uproj";
    public static final String M3U_PROJECT_EXTENSION = "*" + M3U_PROJECT_FILE_SUFFIX;

    private ProjectPersisterUtil() {
    }

    /**
     * @return the description of project-files shown by the file dialogs
     */
    public static String projectFileDescription() {
        return Messages.get("projectFile.filter");
    }

    /**
     * Opens a file dialog to choose a project-file.
     *
     * @return the selected file, or null if the dialog was cancelled
     */
    public static Path openProjectFileDialog() {
        return DialogUtil.getOpenFilePath(projectFileDescription(), M3U_PROJECT_EXTENSION);
    }

    public static DataModel loadProject(Path projectFilePath) {
        DataModel dataModel = null;
        if (projectFilePath != null) {
            try (Reader reader = Files.newBufferedReader(projectFilePath, StandardCharsets.UTF_8)) {
                dataModel = getMapper().readValue(reader, DataModel.class);
            } catch (IOException | JacksonException e) {
                log.error("Exception trying to load project-file: {}", projectFilePath, e);
            }
        }
        if (dataModel == null) {
            log.error("DataModel loaded from project-file was null");
            return null;
        }
        if (isEmpty(dataModel.getName())) {
            log.error("DataModel loaded has an empty name: {}", dataModel);
            return null;
        }
        dataModel.setProjectFile(projectFilePath.toAbsolutePath());
        return dataModel;
    }

    /**
     * Returns the mapper reading and writing project-files (YAML).
     * <p>
     * Like the plugins themselves, the persisted state is defined by their fields: getters and setters are not used,
     * {@code transient} fields and fields annotated with {@link com.fasterxml.jackson.annotation.JsonIgnore} are
     * not persisted.
     * <p>
     * Plugins are stored with their type id, see {@link PluginModule}. Only the plugins provided by the plugin-services
     * are known as types, so a crafted project-file cannot instantiate arbitrary classes.
     */
    // package-private for tests
    static ObjectMapper getMapper() {
        return MapperHolder.MAPPER;
    }

    /**
     * @return the mapper of the settings of the plugins
     */
    // package-private for tests
    static ObjectMapper getSettingsMapper() {
        return MapperHolder.SETTINGS_MAPPER;
    }

    /**
     * @return the state of the plugin as it is stored in the project-file, e.g. to find out whether it has been
     * changed; null if it cannot be written
     */
    public static String persistedState(Plugin plugin) {
        try {
            // the settings, not whether it is enabled
            return MapperHolder.SETTINGS_MAPPER.writeValueAsString(plugin.getSettings());
        } catch (JacksonException e) {
            log.warn("The state of {} cannot be written", plugin, e);
            return null;
        }
    }

    private static final class MapperHolder {
        /**
         * Maps the settings of the plugins, without the {@link PluginModule}.
         */
        private static final ObjectMapper SETTINGS_MAPPER = builder().build();
        private static final ObjectMapper MAPPER = builder()
                .addModule(new PluginModule(() -> SETTINGS_MAPPER))
                .build();
    }

    private static YAMLMapper.Builder builder() {
        return YAMLMapper.builder()
                .changeDefaultVisibility(visibility -> visibility
                        .withFieldVisibility(Visibility.ANY)
                        .withGetterVisibility(Visibility.NONE)
                        .withIsGetterVisibility(Visibility.NONE)
                        .withSetterVisibility(Visibility.NONE))
                // fields without value are omitted, they keep their initial value when loading
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                // keep the order of the fields, which is more readable than an alphabetical order
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                // enums are stored by their name, not their (display-)label
                .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
                .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // no YAML-tags ("!<FileInput>"), the plugins are stored with their type as regular property
                .disable(YAMLWriteFeature.USE_NATIVE_TYPE_ID)
                .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
                .enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
                .addModule(new JavaFxModule())
                // the entries of the API carry no annotations: their position is set when shown, it is not stored
                .addMixIn(Entry.class, EntryMixIn.class);
    }

    /**
     * The storage of the entries of manual changes.
     */
    private abstract static class EntryMixIn {
        @JsonIgnore
        private int index;
    }

    /**
     * Result of saving a project via the save-dialog.
     */
    public enum SaveResult {
        SAVED, CANCELLED, FAILED
    }

    /**
     * Saves the project to the given file.
     * The project-file is created completely before writing, and the file is replaced atomically (where supported),
     * so an existing project-file is not destroyed if saving fails.
     *
     * @return true if the project has been saved
     */
    public static boolean saveProjectFile(String fileName, DataModel dataModel) {
        log.debug("saving projectFile to: {}", fileName);
        Path target = Path.of(fileName).toAbsolutePath();
        Path tempFile = null;
        try {
            String yaml = getMapper().writeValueAsString(dataModel);
            tempFile = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            Files.writeString(tempFile, yaml, StandardCharsets.UTF_8);
            moveReplacing(tempFile, target);
            return true;
        } catch (IOException | JacksonException e) {
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
     * Saves the project to the file it has been loaded from or saved to before.
     * Opens a save-dialog for a new project.
     */
    public static SaveResult saveProject(DataModel dataModel) {
        if (dataModel.getProjectFile() == null) {
            return openSaveDialogAndSaveProject(dataModel);
        }
        return saveProjectTo(dataModel.getProjectFile(), dataModel);
    }

    /**
     * Opens a save-dialog and saves the project to the selected file ("save as").
     */
    public static SaveResult openSaveDialogAndSaveProject(DataModel dataModel) {
        log.debug("Saving the Project");
        Path saveFilePath = DialogUtil.getSaveFilePath(projectFileDescription(), M3U_PROJECT_EXTENSION);
        return saveProjectAs(dataModel, saveFilePath, DialogUtil::canFileBeOverwritten);
    }

    /**
     * Saves the project to the file selected in the save-dialog, appending the suffix of project-files if missing.
     * <p>
     * The save-dialog only asks before replacing the file with the name entered. If the suffix is appended,
     * an existing file with that name is only replaced if confirmed.
     *
     * @param selectedFile          the file selected in the save-dialog, null if cancelled
     * @param overwriteConfirmation asks whether an existing file may be replaced
     */
    static SaveResult saveProjectAs(DataModel dataModel, Path selectedFile, Predicate<Path> overwriteConfirmation) {
        if (selectedFile == null) {
            return SaveResult.CANCELLED;
        }
        Path projectFile = withProjectFileSuffix(selectedFile);
        if (!projectFile.equals(selectedFile) && Files.exists(projectFile) && !overwriteConfirmation.test(projectFile)) {
            log.debug("Replacing existing project-file {} not confirmed", projectFile);
            return SaveResult.CANCELLED;
        }
        return saveProjectTo(projectFile, dataModel);
    }

    /**
     * Appends the suffix of project-files ({@value #M3U_PROJECT_FILE_SUFFIX}) to the file-name, if it is missing.
     * The save-dialog does not add it on all platforms.
     */
    static Path withProjectFileSuffix(Path projectFile) {
        String fileName = projectFile.getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).endsWith(M3U_PROJECT_FILE_SUFFIX)) {
            return projectFile;
        }
        return projectFile.resolveSibling(fileName + M3U_PROJECT_FILE_SUFFIX);
    }

    private static SaveResult saveProjectTo(Path projectFile, DataModel dataModel) {
        if (!saveProjectFile(projectFile.toString(), dataModel)) {
            return SaveResult.FAILED;
        }
        dataModel.setProjectFile(projectFile.toAbsolutePath());
        return SaveResult.SAVED;
    }

}
