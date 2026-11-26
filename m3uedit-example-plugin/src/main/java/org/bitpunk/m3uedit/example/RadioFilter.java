package org.bitpunk.m3uedit.example;

import lombok.Getter;
import lombok.Setter;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;

import java.util.List;

/**
 * An example of a plugin of another party: removes the radio channels of the playlist (or keeps only them).
 * <p>
 * It is built as a jar of its own against the plugin API only, like any plugin not part of m3uedit, and installed by
 * copying the jar into {@code ~/.m3uedit/extensions/}. Its texts are in {@code org/bitpunk/m3uedit/example/messages*}.
 */
@PluginType(id = "org.bitpunk.m3uedit.example.RadioFilter", nameKey = "radioFilter.name",
        texts = "org.bitpunk.m3uedit.example.messages")
public class RadioFilter implements Filter {
    private PluginContext context;
    @Setter
    @Getter
    private boolean keepRadio;

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return context.text("radioFilter.name");
    }

    @Override
    public String getDescription() {
        return context.text(keepRadio ? "radioFilter.description.keepRadio" : "radioFilter.description");
    }

    @Override
    public List<Entry> filter(List<Entry> entries) {
        return entries.stream().filter(entry -> isRadio(entry) == keepRadio).toList();
    }

    @Override
    public String describeRemoval(Entry removed, List<Entry> input) {
        return context.text(isRadio(removed) ? "radioFilter.reason.radio" : "radioFilter.reason.tv");
    }

    private static boolean isRadio(Entry entry) {
        return "true".equalsIgnoreCase(entry.getRadio());
    }

    @Override
    public Class<RadioFilterSettings> getSettingsType() {
        return RadioFilterSettings.class;
    }

    @Override
    public RadioFilterSettings getSettings() {
        return new RadioFilterSettings(keepRadio);
    }

    @Override
    public void setSettings(Record settings) {
        // missing in an older project-file: remove the radio channels
        Boolean keep = ((RadioFilterSettings) settings).keepRadio();
        keepRadio = keep != null && keep;
    }

}
