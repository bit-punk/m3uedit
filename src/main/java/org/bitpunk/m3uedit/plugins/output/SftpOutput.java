package org.bitpunk.m3uedit.plugins.output;

import lombok.Getter;
import org.bitpunk.m3uedit.model.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Getter
public class SftpOutput implements Output {
    private static final Logger log = LoggerFactory.getLogger(SftpOutput.class);
    private final String name = "SFTP Output";
    private final String description = "writes a playlist to a SFTP server.";
    private String output = "";

    @Override
    public void write(List<Entry> playlist) {
        log.debug("write()");
    }

    @Override
    public void configure() {
        log.debug("configure()");

    }
}