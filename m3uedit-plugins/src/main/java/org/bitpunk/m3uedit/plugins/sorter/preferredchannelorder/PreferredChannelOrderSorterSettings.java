package org.bitpunk.m3uedit.plugins.sorter.preferredchannelorder;

/**
 * The settings of the {@link PreferredChannelOrderSorter} stored in the project-file; its profiles are stored in its
 * configuration-file.
 *
 * @param selectedProfileId the id of the profile used for sorting, the default profile of the configuration if null
 */
public record PreferredChannelOrderSorterSettings(String selectedProfileId) {
}
