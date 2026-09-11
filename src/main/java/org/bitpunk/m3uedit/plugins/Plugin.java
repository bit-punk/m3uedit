package org.bitpunk.m3uedit.plugins;

import javafx.beans.property.BooleanProperty;
import org.bitpunk.m3uedit.NamedItem;

public interface Plugin extends NamedItem {
    String getDescription();
    BooleanProperty getEnabled();
    default void configure() {}
}