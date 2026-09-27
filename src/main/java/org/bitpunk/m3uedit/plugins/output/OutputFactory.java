package org.bitpunk.m3uedit.plugins.output;

import org.bitpunk.m3uedit.plugins.output.file.FileOutput;
import org.bitpunk.m3uedit.plugins.output.sftp.SftpOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class OutputFactory {
    private static final Logger log = LoggerFactory.getLogger(OutputFactory.class);
    private static final Map<String, Supplier<Output>> outputMap = new HashMap<>();

    static {
        initMap();
    }

    private OutputFactory() {
    }

    private static void initMap() {
        outputMap.put("File Output", () -> {
            log.debug("creating new File Output");
            return new FileOutput();
        });
        outputMap.put("SFTP Output", () -> {
            log.debug("creating new SFTP Output");
            return new SftpOutput();
        });
    }

    public static Output getOutput(String outputName) {
        if (outputMap.containsKey(outputName)) {
            return outputMap.get(outputName).get();
        }
        return null;
    }
}