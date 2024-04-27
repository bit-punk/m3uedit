package org.bitpunk.m3uedit.plugins.input;

import lombok.Getter;

@Getter
public enum InputType {
    FILE_INPUT("File Input"),
    FRITZBOX_INPUT("FritzBox Input"),
    URL_INPUT("Url Input");

    private final String type;

    InputType(String type) {
        this.type = type;
    }

    public static InputType fromString(String type) {
        for (InputType inputType : InputType.values()) {
            if (inputType.getType().equals(type)) {
                return inputType;
            }
        }
        throw new IllegalArgumentException("Invalid input type: " + type);
    }
}