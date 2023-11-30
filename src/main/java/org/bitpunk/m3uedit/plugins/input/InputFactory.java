package org.bitpunk.m3uedit.plugins.input;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputFactory {
    private static final Logger log = LoggerFactory.getLogger(InputFactory.class);

    private InputFactory() {
    }

    public static Input getInput(String inputName) {
        InputType inputType = InputType.fromString(inputName);
        return switch (inputType) {
            case FILE_INPUT -> {
                log.debug("creating new File Input");
                yield new FileInput();
            }
            case FRITZBOX_INPUT -> {
                log.debug("creating new Fritzbox Input");
                yield new FritzBoxInput();
            }
        };
    }
}