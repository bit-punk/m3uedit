package org.bitpunk.m3uedit.plugins.input;

import org.bitpunk.m3uedit.plugins.input.file.FileInput;
import org.bitpunk.m3uedit.plugins.input.fritzbox.FritzBoxInput;
import org.bitpunk.m3uedit.plugins.input.iptvorg.IptvOrgInput;
import org.bitpunk.m3uedit.plugins.input.sftp.SftpInput;
import org.bitpunk.m3uedit.plugins.input.url.UrlInput;
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
            case URL_INPUT -> {
                log.debug("creating new Url Input");
                yield new UrlInput();
            }
            case IPTV_ORG_INPUT -> {
                log.debug("creating new IPTV-org Input");
                yield new IptvOrgInput();
            }
            case SFTP_INPUT -> {
                log.debug("creating new SFTP Input");
                yield new SftpInput();
            }
        };
    }
}