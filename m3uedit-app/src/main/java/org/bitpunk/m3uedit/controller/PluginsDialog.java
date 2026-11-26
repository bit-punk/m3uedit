package org.bitpunk.m3uedit.controller;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import lombok.Getter;
import org.bitpunk.m3uedit.api.Enricher;
import org.bitpunk.m3uedit.api.Filter;
import org.bitpunk.m3uedit.api.Input;
import org.bitpunk.m3uedit.api.Output;
import org.bitpunk.m3uedit.api.Sorter;
import org.bitpunk.m3uedit.i18n.Messages;
import org.bitpunk.m3uedit.plugins.PluginDescriptor;
import org.bitpunk.m3uedit.plugins.PluginJars;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The plugins of m3uedit (<i>Help → Plugins</i>): the built-in ones, those installed by the user as jars in
 * {@code ~/.m3uedit/extensions/}, and the jars that could not be loaded, with the reason.
 */
public class PluginsDialog extends Dialog<Void> {

    @Getter
    private final TableView<PluginDescriptor<?>> pluginTable = new TableView<>();
    @Getter
    private final TableView<PluginJars.Problem> problemTable = new TableView<>();

    /**
     * @param plugins  the plugins of all kinds
     * @param jars     the jars installed by the user, with their problems
     */
    public PluginsDialog(List<PluginDescriptor<?>> plugins, PluginJars jars) {
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
        setTitle(Messages.get("pluginsDialog.title"));
        setHeaderText(null);
        getDialogPane().getStyleClass().add("plugins-dialog");

        pluginTable.getColumns().add(column(Messages.get("pluginsDialog.column.name"), PluginDescriptor::name));
        pluginTable.getColumns().add(column(Messages.get("pluginsDialog.column.kind"), PluginsDialog::kind));
        pluginTable.getColumns().add(column(Messages.get("pluginsDialog.column.id"), PluginDescriptor::id));
        pluginTable.getColumns().add(column(Messages.get("pluginsDialog.column.source"), plugin -> plugin.isInstalled()
                ? Objects.toString(plugin.jar().getFileName()) : Messages.get("pluginsDialog.builtIn")));
        pluginTable.getItems().setAll(plugins);
        pluginTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        pluginTable.setPrefHeight(300);

        VBox content = new VBox(8, pluginTable);
        List<PluginJars.Problem> problems = jars.getProblems();
        if (!problems.isEmpty()) {
            problemTable.getColumns().add(column(Messages.get("pluginsDialog.column.jar"),
                    problem -> Objects.toString(problem.file().getFileName())));
            problemTable.getColumns().add(column(Messages.get("pluginsDialog.column.reason"), PluginJars.Problem::reason));
            problemTable.getItems().setAll(problems);
            problemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            problemTable.setPrefHeight(120);
            Label problemTitle = new Label(Messages.get("pluginsDialog.problems"));
            problemTitle.getStyleClass().add("plugins-problems");
            problemTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: -app-error;");
            content.getChildren().addAll(problemTitle, problemTable);
        }
        // the directory can be copied, e.g. to open it in a file manager
        TextField directory = new TextField(Objects.toString(PluginJars.directory()));
        directory.setEditable(false);
        Label install = wrapped(Messages.get("pluginsDialog.install"));
        Label security = wrapped(Messages.get("pluginsDialog.security"));
        content.getChildren().addAll(install, directory, security);

        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(760);
        getDialogPane().getButtonTypes().add(new ButtonType(Messages.get("common.close"),
                ButtonBar.ButtonData.CANCEL_CLOSE));
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private static <S> TableColumn<S, String> column(String title, Function<S, String> value) {
        TableColumn<S, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    /**
     * @return the kind of plugin, e.g. "Input"
     */
    static String kind(PluginDescriptor<?> plugin) {
        Class<?> type = plugin.type();
        if (Input.class.isAssignableFrom(type)) {
            return Messages.get("pluginsDialog.kind.input");
        } else if (Filter.class.isAssignableFrom(type)) {
            return Messages.get("pluginsDialog.kind.filter");
        } else if (Sorter.class.isAssignableFrom(type)) {
            return Messages.get("pluginsDialog.kind.sorter");
        } else if (Enricher.class.isAssignableFrom(type)) {
            return Messages.get("pluginsDialog.kind.enricher");
        } else if (Output.class.isAssignableFrom(type)) {
            return Messages.get("pluginsDialog.kind.output");
        }
        return "";
    }
}
