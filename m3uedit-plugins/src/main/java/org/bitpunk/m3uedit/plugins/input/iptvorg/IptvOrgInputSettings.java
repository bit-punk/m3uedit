package org.bitpunk.m3uedit.plugins.input.iptvorg;

import org.bitpunk.m3uedit.plugins.input.iptvorg.model.IptvOrgPlaylist;

import java.util.List;

/**
 * The settings of an {@link IptvOrgInput} stored in the project-file.
 *
 * @param selectedPlaylists the playlists and channels read
 */
public record IptvOrgInputSettings(List<IptvOrgPlaylist> selectedPlaylists) {
}
