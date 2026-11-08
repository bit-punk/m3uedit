package org.bitpunk.m3uedit.plugins;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javafx.beans.property.BooleanProperty;
import org.bitpunk.m3uedit.NamedItem;

/**
 * In a project-file, each plugin is stored with its simple class-name as type, e.g. {@code type: FileInput}.
 * Only plugins provided by the plugin-services are accepted as types when loading a project.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = Plugin.TYPE_PROPERTY)
public interface Plugin extends NamedItem {
    String TYPE_PROPERTY = "type";

    String getDescription();
    BooleanProperty getEnabled();
    default void configure() {}
    default boolean isConfigurable() {
        return false;
    }
}
