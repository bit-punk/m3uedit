package org.bitpunk.m3uedit.plugins.input;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InputFactory {
    private static final Logger log = LoggerFactory.getLogger(InputFactory.class);

    private InputFactory() {
    }

    public static Input getInput(String inputName) {
        Input input = null;
        if (inputName.equals("File Input")) {
            log.debug("creating new File Input");
            input = new FileInput();
        } else if (inputName.equals("FritzBox Input")) {
            log.debug("creating new Fritzbox Input");
            input = new FritzBoxInput();
        }
        return input;
    }
}
