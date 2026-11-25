package org.bitpunk.m3uedit.controller;

import lombok.Data;
import org.bitpunk.m3uedit.api.NamedItem;

/**
 * Wrapper class for Map.Entry implementing the <i>NamedItem</i>-interface. Used by the {@link MapEditorController} to
 * map the HasMap-content for vlcOptions to an of List<NamedEntry>-object, so that the delete-handler of the
 * {@link AbstractController} works.
 */
@Data
public class NamedEntry implements NamedItem {
    private String key;
    private String value;
    private boolean valid;
    private String validationError;

    public NamedEntry(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String getName() {
        return "%s=%s".formatted(key, value);
    }
}