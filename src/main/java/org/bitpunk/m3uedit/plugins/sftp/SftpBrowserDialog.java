package org.bitpunk.m3uedit.plugins.sftp;

import com.jcraft.jsch.SftpException;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.bitpunk.m3uedit.plugins.sftp.SftpClient.RemoteEntry;
import org.bitpunk.m3uedit.plugins.sftp.model.SftpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lets the user pick the target directory (and optionally an existing file) on an SFTP server.
 * <p>
 * Connecting and listing run in a background thread; a single thread is used so the SFTP channel
 * (which is not thread-safe) is only ever accessed sequentially.
 */
public class SftpBrowserDialog extends Dialog<SftpBrowserDialog.Selection> {
    private static final Logger log = LoggerFactory.getLogger(SftpBrowserDialog.class);
    static final RemoteEntry PARENT_ENTRY = new RemoteEntry("..", true);

    /**
     * @param directory the selected directory
     * @param fileName  the selected existing file, or null if only a directory was chosen
     */
    public record Selection(String directory, String fileName) {
    }

    private record Listing(String directory, List<RemoteEntry> entries, String message) {
    }

    private final SftpConnection connection;
    private final SftpUserInteraction userInteraction;
    private final boolean fileRequired;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sftp-browser");
        thread.setDaemon(true);
        return thread;
    });
    private final Label pathLabel = new Label();
    private final Label statusLabel = new Label();
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final ListView<RemoteEntry> listView = new ListView<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty connected = new SimpleBooleanProperty(false);
    private volatile SftpClient client;
    private String currentDirectory;

    /**
     * @param connection     connection data including the password
     * @param startDirectory directory to show first; falls back to the home directory if it does not exist
     * @param fileRequired   true if an existing file has to be selected (input), false if a directory is enough (output)
     */
    public SftpBrowserDialog(SftpConnection connection, String startDirectory, SftpUserInteraction userInteraction,
                             boolean fileRequired) {
        this.connection = connection;
        this.userInteraction = userInteraction;
        this.fileRequired = fileRequired;
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Browse SFTP Server");
        setHeaderText(connection.getUserName() + "@" + connection.getHostName() + ":" + connection.getPort());
        setResizable(true);
        initContent();
        setOnHidden(event -> shutdown());
        connectAndLoad(startDirectory);
    }

    public boolean isConnected() {
        return connected.get();
    }

    private void initContent() {
        ButtonType selectButtonType = new ButtonType("Select", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(selectButtonType, ButtonType.CANCEL);
        Button selectButton = (Button) getDialogPane().lookupButton(selectButtonType);
        BooleanBinding noFileSelected = Bindings.createBooleanBinding(() -> {
            RemoteEntry selected = listView.getSelectionModel().getSelectedItem();
            return selected == null || selected.directory();
        }, listView.getSelectionModel().selectedItemProperty());
        selectButton.disableProperty().bind(fileRequired
                ? loading.or(connected.not()).or(noFileSelected)
                : loading.or(connected.not()));

        progressIndicator.setPrefSize(16, 16);
        progressIndicator.visibleProperty().bind(loading);
        progressIndicator.managedProperty().bind(loading);
        statusLabel.setWrapText(true);
        pathLabel.setStyle("-fx-font-weight: bold;");

        listView.setPrefSize(450, 350);
        listView.disableProperty().bind(loading);
        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(RemoteEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                setText(empty || entry == null ? null : displayName(entry));
            }
        });
        listView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelectedEntry(selectButton);
            }
        });
        listView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                openSelectedEntry(selectButton);
                event.consume();
            }
        });

        Label hint = new Label(fileRequired
                ? "Double-click opens a directory or selects a playlist file."
                : "Double-click opens a directory. Select a file to overwrite it.");
        hint.setStyle("-fx-opacity: 0.7;");
        HBox statusBox = new HBox(5, progressIndicator, statusLabel);
        VBox content = new VBox(8, pathLabel, listView, hint, statusBox);
        content.setPadding(new Insets(5));
        VBox.setVgrow(listView, Priority.ALWAYS);
        getDialogPane().setContent(content);

        setResultConverter(buttonType -> {
            if (buttonType != selectButtonType || currentDirectory == null) {
                return null;
            }
            RemoteEntry selected = listView.getSelectionModel().getSelectedItem();
            String fileName = selected != null && !selected.directory() ? selected.name() : null;
            return new Selection(currentDirectory, fileName);
        });
    }

    static String displayName(RemoteEntry entry) {
        if (entry == PARENT_ENTRY) {
            return ".. (parent directory)";
        }
        return entry.directory() ? entry.name() + "/" : entry.name();
    }

    static List<RemoteEntry> withParentEntry(String directory, List<RemoteEntry> entries) {
        if ("/".equals(directory)) {
            return entries;
        }
        List<RemoteEntry> result = new ArrayList<>(entries.size() + 1);
        result.add(PARENT_ENTRY);
        result.addAll(entries);
        return result;
    }

    /**
     * Opens the selected directory; a selected file is taken over directly (like pressing "Select").
     */
    private void openSelectedEntry(Button selectButton) {
        RemoteEntry selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null || loading.get() || currentDirectory == null) {
            return;
        }
        if (!selected.directory()) {
            selectButton.fire();
            return;
        }
        String target = selected == PARENT_ENTRY
                ? SftpClient.parent(currentDirectory)
                : SftpClient.child(currentDirectory, selected.name());
        runInBackground("Loading " + target + " ...", () -> loadDirectory(target, null));
    }

    private void connectAndLoad(String startDirectory) {
        runInBackground("Connecting ...", () -> {
            client = SftpClient.connect(connection, userInteraction);
            try {
                return loadDirectory(startDirectory, null);
            } catch (SftpException e) {
                log.debug("Start directory '{}' not available ({}), showing home directory", startDirectory, e.getMessage());
                return loadDirectory(".", "Directory '" + startDirectory + "' not found, showing home directory.");
            }
        });
    }

    private Listing loadDirectory(String directory, String message) throws SftpException {
        String canonical = client.canonicalPath(directory);
        return new Listing(canonical, withParentEntry(canonical, client.list(canonical)), message);
    }

    private void runInBackground(String statusText, Callable<Listing> work) {
        Task<Listing> task = new Task<>() {
            @Override
            protected Listing call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            Listing listing = task.getValue();
            currentDirectory = listing.directory();
            pathLabel.setText(listing.directory());
            listView.getItems().setAll(listing.entries());
            statusLabel.setText(listing.message() == null ? "" : listing.message());
            connected.set(client != null);
            loading.set(false);
            listView.requestFocus();
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            log.warn("SFTP browser: {}", error.getMessage(), error);
            statusLabel.setText(SftpClient.describeError(error));
            connected.set(client != null);
            loading.set(false);
        });
        statusLabel.setText(statusText);
        loading.set(true);
        executor.submit(task);
    }


    private void shutdown() {
        executor.submit(() -> {
            if (client != null) {
                client.close();
            }
        });
        executor.shutdown();
    }
}
