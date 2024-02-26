package org.bitpunk.m3uedit.plugins;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Data;

@Data
abstract public class AbstractPlugin {
    private String name;
    private String description;
    private BooleanProperty enabled = new SimpleBooleanProperty();
}
