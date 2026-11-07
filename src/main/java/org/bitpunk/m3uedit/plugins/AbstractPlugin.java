package org.bitpunk.m3uedit.plugins;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;

@Data
public abstract class AbstractPlugin {
    private String name;
    private String description;
    @Getter(AccessLevel.NONE)
    private BooleanProperty enabled = new SimpleBooleanProperty();

    public BooleanProperty getEnabled() {
        // XStream does not run field initializers, so 'enabled' is null for project-files without this element
        if (enabled == null) {
            enabled = new SimpleBooleanProperty();
        }
        return enabled;
    }
}
