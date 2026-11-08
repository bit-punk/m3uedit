package org.bitpunk.m3uedit.plugins;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Setter;
import lombok.Getter;

@Getter
@Setter
public abstract class AbstractPlugin {
    // name and description are constants of each plugin, they are not part of a project-file
    @JsonIgnore
    private String name;
    @JsonIgnore
    private String description;
    private BooleanProperty enabled = new SimpleBooleanProperty();

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
