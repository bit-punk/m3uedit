package org.bitpunk.m3uedit.plugins;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * A plugin without function, e.g. to test how plugins are found.
 */
class TestPlugin implements Plugin {
    private final BooleanProperty enabled = new SimpleBooleanProperty();

    @Override
    public String getName() {
        return "test plugin";
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public BooleanProperty getEnabled() {
        return enabled;
    }
}
