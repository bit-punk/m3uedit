package org.bitpunk.m3uedit.plugins;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Setter;
import lombok.AccessLevel;
import lombok.Getter;

@Getter
@Setter
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

    /**
     * Plugins are compared by identity: their fields are mutable (configuration) and there may be several
     * equally configured instances in one project. Only a short description is returned here, as the configuration
     * may be large (e.g. lists of urls or playlists).
     */
    @Override
    public String toString() {
        return "%s[%s]".formatted(getClass().getSimpleName(), getName());
    }
}
