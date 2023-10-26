package org.bitpunk.m3uedit.plugins.output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutputFactory {
    private static final Logger log = LoggerFactory.getLogger(OutputFactory.class);

    private OutputFactory() {
    }

    public static Output getOutput(String outputName) {
        Output output = null;
        if (outputName.equals("File Output")) {
            log.debug("creating new File Output");
            output = new FileOutput();
        } else if (outputName.equals("SFTP Output")) {
            log.debug("creating new SFTP Output");
            output = new SftpOutput();
        }
        return output;
    }
}
