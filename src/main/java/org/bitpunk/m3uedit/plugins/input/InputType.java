package org.bitpunk.m3uedit.plugins.input;

import lombok.Getter;
import org.bitpunk.m3uedit.i18n.Messages;

@Getter
public enum InputType {
    FILE_INPUT("File Input", "input.file.name"),
    FRITZBOX_INPUT("FritzBox Input", "input.fritzbox.name"),
    URL_INPUT("Url Input", "input.url.name"),
    IPTV_ORG_INPUT("IPTV-org Input", "input.iptvorg.name"),
    SFTP_INPUT("SFTP Input", "input.sftp.name");

    private final String type;
    /**
     * The key of the name shown to the user, which is the name of the input in the language of the user interface.
     */
    private final String nameKey;

    InputType(String type, String nameKey) {
        this.type = type;
        this.nameKey = nameKey;
    }

    /**
     * @return the name of the input shown to the user (in English: the type)
     */
    public String getDisplayName() {
        return Messages.get(nameKey);
    }

    /**
     * @param type the type, or the name shown to the user (e.g. offered by the add-menu of the pipeline)
     */
    public static InputType fromString(String type) {
        for (InputType inputType : InputType.values()) {
            if (inputType.getType().equals(type) || inputType.getDisplayName().equals(type)) {
                return inputType;
            }
        }
        throw new IllegalArgumentException("Invalid input type: " + type);
    }
}
