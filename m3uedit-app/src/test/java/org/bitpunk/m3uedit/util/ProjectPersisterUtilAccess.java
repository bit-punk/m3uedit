package org.bitpunk.m3uedit.util;

import tools.jackson.databind.ObjectMapper;

/**
 * The mapper of project-files for tests of other packages.
 */
public final class ProjectPersisterUtilAccess {

    private ProjectPersisterUtilAccess() {
        // Utility class
    }

    public static ObjectMapper mapper() {
        return ProjectPersisterUtil.getMapper();
    }
}
