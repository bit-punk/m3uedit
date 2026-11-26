package org.bitpunk.m3uedit.api;

/**
 * The version of the plugin API, checked when m3uedit loads the jar of a plugin.
 * <p>
 * A plugin jar declares the version it was built for in its manifest:
 * <pre>
 * M3UEdit-Plugin-API: 1
 * </pre>
 * m3uedit only loads jars built for its version; the version is raised with every change of the API that is not
 * compatible with plugins built before.
 */
public final class PluginApi {

    /**
     * The version of this API.
     */
    public static final int VERSION = 1;

    /**
     * The attribute of the manifest of a plugin jar declaring the version of the API it was built for.
     */
    public static final String MANIFEST_ATTRIBUTE = "M3UEdit-Plugin-API";

    private PluginApi() {
        // constants only
    }
}
