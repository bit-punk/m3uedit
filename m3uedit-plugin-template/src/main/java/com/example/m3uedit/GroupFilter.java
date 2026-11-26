package com.example.m3uedit;

import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.bitpunk.m3uedit.api.Entry;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.PluginContext;
import org.bitpunk.m3uedit.api.PluginType;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Removes the entries of chosen groups (the {@code group-title} of their {@code #EXTINF} line), or keeps only them.
 * <p>
 * The type id is stored in the project-files of m3uedit: it must never change once the plugin is used, also when the
 * class is renamed or moved. The texts are in {@code com/example/m3uedit/messages*.properties}; the plugin is
 * registered in {@code META-INF/services/org.bitpunk.m3uedit.api.Filter}.
 */
@PluginType(id = "com.example.m3uedit.GroupFilter", nameKey = "groupFilter.name",
        texts = "com.example.m3uedit.messages")
public class GroupFilter implements Filter {
    private PluginContext context;
    private GroupFilterSettings settings = GroupFilterSettings.defaults();

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return context.text("groupFilter.name");
    }

    @Override
    public String getDescription() {
        if (settings.groups().isEmpty()) {
            return context.text("groupFilter.description.none");
        }
        String key = settings.keepGroups() ? "groupFilter.description.keep" : "groupFilter.description";
        return context.text(key, settings.groups().size());
    }

    /**
     * Runs in the background (and in the command-line mode): no JavaFX here.
     */
    @Override
    public List<Entry> filter(List<Entry> entries) {
        if (settings.groups().isEmpty()) {
            return entries;
        }
        Set<String> groups = settings.groups().stream().map(GroupFilter::normalize).collect(Collectors.toSet());
        return entries.stream()
                .filter(entry -> groups.contains(normalize(entry.getGroupTitle())) == settings.keepGroups())
                .toList();
    }

    @Override
    public String describeRemoval(Entry removed, List<Entry> input) {
        String group = removed.getGroupTitle();
        return group == null || group.isBlank()
                ? context.text("groupFilter.reason.none")
                : context.text("groupFilter.reason", group);
    }

    private static String normalize(String group) {
        return group == null ? "" : group.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public Class<GroupFilterSettings> getSettingsType() {
        return GroupFilterSettings.class;
    }

    @Override
    public GroupFilterSettings getSettings() {
        return settings;
    }

    @Override
    public void setSettings(Record settings) {
        // the record replaces components missing in an older project-file by the defaults
        this.settings = (GroupFilterSettings) settings;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    /**
     * Called on the JavaFX application thread, never in the command-line mode.
     */
    @Override
    public boolean configure(Window owner) {
        TextArea groups = new TextArea(String.join("\n", settings.groups()));
        groups.setPrefRowCount(8);
        CheckBox keep = new CheckBox(context.text("groupFilter.dialog.keep"));
        keep.setSelected(settings.keepGroups());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(context.text("groupFilter.dialog.title"));
        dialog.getDialogPane().setContent(new VBox(8, new Label(context.text("groupFilter.dialog.groups")), groups, keep));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return false;
        }
        List<String> lines = Arrays.stream(groups.getText().split("\\R"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .distinct()
                .toList();
        settings = new GroupFilterSettings(lines, keep.isSelected());
        return true;
    }
}
