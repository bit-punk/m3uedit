package org.bitpunk.m3uedit.plugins.support;

import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.Modality;
import org.bitpunk.m3uedit.api.PluginContext;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The base of the dialogs of the built-in plugins: a dialog pane loaded from an FXML file, whose {@code %key}
 * references are the texts of the plugin.
 *
 * @param <T> the result of the dialog
 */
public abstract class PluginDialogController<T> extends Dialog<T> implements Initializable {
    /**
     * The style of an input field containing an invalid value, using the colours of the theme of m3uedit
     * ({@code -app-error}, {@code -app-invalid-background}), so it looks right in the light and the dark theme.
     */
    public static final String INVALID_INPUT_STYLE = "-fx-text-box-border: -app-error; -fx-focus-color: -app-error; "
            + "-fx-control-inner-background: -app-invalid-background; -fx-accent: -app-error;";

    /**
     * @param context      the context of the plugin, for the texts of the FXML file
     * @param fxmlResource the FXML file of the dialog pane, a resource of the plugin
     */
    protected PluginDialogController(PluginContext context, String fxmlResource, String title) {
        initModality(Modality.APPLICATION_MODAL);
        setResizable(true);
        setTitle(title);
        setDialogPane(load(context, fxmlResource));
    }

    private DialogPane load(PluginContext context, String fxmlResource) {
        FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource(fxmlResource),
                context.resourceBundle());
        loader.setController(this);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load FXML file: " + fxmlResource, e);
        }
    }

    protected void markFieldInvalid(TextField textField, String errorMessage) {
        textField.setStyle(INVALID_INPUT_STYLE);
        textField.setTooltip(new Tooltip(errorMessage));
    }

    protected void resetTextField(TextField textField) {
        textField.setStyle(null);
        textField.setTooltip(null);
    }
}
