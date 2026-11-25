package org.bitpunk.m3uedit.plugins.input.url;

import java.util.List;

/**
 * The settings of a {@link UrlInput} stored in the project-file.
 *
 * @param selectedInputUrls the urls of the playlists read
 * @param urlList           the urls confirmed in the dialog, shown as source of the input (the same as the
 *                          selected urls; stored for the project-files of older versions)
 */
public record UrlInputSettings(List<String> selectedInputUrls, List<String> urlList) {
}
