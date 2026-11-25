package org.bitpunk.m3uedit.plugins;

import org.bitpunk.m3uedit.api.Plugin;

/**
 * A plugin without function, e.g. to test how plugins are found.
 */
class TestPlugin implements Plugin {

    @Override
    public String getName() {
        return "test plugin";
    }

    @Override
    public String getDescription() {
        return "";
    }
}
