package org.bitpunk.m3uedit.config;

import java.nio.file.Path;
import java.time.Instant;

/**
 * A recently used project-file.
 *
 * @param path       the absolute path of the project-file
 * @param lastOpened when it was opened or saved the last time
 */
public record RecentProject(String path, Instant lastOpened) {
    private static final String PROJECT_FILE_SUFFIX = ".m3uproj";

    public Path file() {
        return Path.of(path);
    }

    /**
     * @return the name of the project-file without its suffix
     */
    public String name() {
        String fileName = file().getFileName() == null ? path : file().getFileName().toString();
        return fileName.endsWith(PROJECT_FILE_SUFFIX)
                ? fileName.substring(0, fileName.length() - PROJECT_FILE_SUFFIX.length()) : fileName;
    }

    /**
     * @return the directory of the project-file, empty if it has none
     */
    public String folder() {
        Path parent = file().getParent();
        return parent == null ? "" : parent.toString();
    }
}
