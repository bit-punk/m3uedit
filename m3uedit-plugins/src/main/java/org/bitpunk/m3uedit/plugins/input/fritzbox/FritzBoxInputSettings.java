package org.bitpunk.m3uedit.plugins.input.fritzbox;

import org.bitpunk.m3uedit.plugins.input.fritzbox.model.FritzBoxConnection;
import org.bitpunk.m3uedit.plugins.input.fritzbox.model.StreamSource;

import java.util.List;

/**
 * The settings of a {@link FritzBoxInput} stored in the project-file.
 *
 * @param selectedStreamSources the streams read
 * @param fritzBoxConnection    the FritzBox read from (its base url and the streams selected in the dialog); null if
 *                              not configured yet, then the default base url is used
 */
public record FritzBoxInputSettings(List<StreamSource> selectedStreamSources, FritzBoxConnection fritzBoxConnection) {
}
