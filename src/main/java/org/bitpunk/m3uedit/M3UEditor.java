package org.bitpunk.m3uedit;

import org.bitpunk.m3uedit.builder.Builder;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.PlayList;
import org.bitpunk.m3uedit.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class M3UEditor {
    private static final Logger log = LoggerFactory.getLogger(Builder.class);

    public static void main(String[] args) throws FileNotFoundException {
        Parser parser = new Parser();
        File initialFile = new File("/home/jochen/projekte/M3UEditor/m3uedit/src/test/resources/tvsd.m3u");
        InputStream inputStream = new DataInputStream(new FileInputStream(initialFile));
        PlayList<Entry> playList = parser.parse(inputStream);
//        log.debug("reda Playlist: {}", playList);
        printPlayList(playList);
    }

    private static void printPlayList(PlayList playList){
        playList.stream().forEach(entry -> System.out.println(((Entry)entry).getChannelName()));

    }
}
