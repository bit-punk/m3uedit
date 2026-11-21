package org.bitpunk.m3uedit.plugins.output;

import lombok.extern.slf4j.Slf4j;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.plugins.output.file.FileOutput;
import org.bitpunk.m3uedit.plugins.output.sftp.SftpOutput;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
public class OutputFactory {
    private static final Map<String, Supplier<Output>> outputMap = new HashMap<>();
    /**
     * The keys of the names shown to the user, per name of an output.
     */
    private static final Map<String, String> nameKeys = Map.of("File Output", "output.file.name",
            "SFTP Output", "output.sftp.name");

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

    /**
     * @param outputName the name of the output, in English or as shown to the user (e.g. offered by the add-menu of
     *                   the pipeline)
     */
    public static Output getOutput(String outputName) {
        String name = nameKeys.entrySet().stream()
                .filter(nameKey -> Messages.get(nameKey.getValue()).equals(outputName))
                .map(Map.Entry::getKey).findFirst().orElse(outputName);
        if (outputMap.containsKey(name)) {
            return outputMap.get(name).get();
        }
        return null;
    }
}