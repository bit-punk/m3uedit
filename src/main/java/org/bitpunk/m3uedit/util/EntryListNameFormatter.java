package org.bitpunk.m3uedit.util;

import javafx.collections.ObservableList;
import org.bitpunk.m3uedit.NamedItem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EntryListNameFormatter {

    public static final int MAX_NAMES = 5;

    private EntryListNameFormatter() {
        // hide constructor of this util-class
    }

    public static String getNamesSummary(ObservableList<NamedItem> deleteItems) {
        List<String> namesList = convertToStrings(deleteItems);
        StringBuilder entryNames = new StringBuilder();
        if (deleteItems.size() > MAX_NAMES) {
            List<String> summary = new ArrayList<>(namesList.subList(0, 2));
            summary.add("...");
            summary.addAll(namesList.subList(deleteItems.size() - 2, namesList.size()));
            namesList = summary;
        }
        namesList.forEach(name -> entryNames.append("• ").append(name).append("\n"));
        return entryNames.toString();
    }

    private static List<String> convertToStrings(ObservableList<NamedItem> namedItems) {
        return namedItems.stream().map(NamedItem::getName).collect(Collectors.toList());
    }
}