package org.bitpunk.m3uedit;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.PlayList;
import org.bitpunk.m3uedit.parser.Parser;

import java.io.*;

public class M3UEditor extends Application {
    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");
    final HBox hb = new HBox();
    private final TableView<Entry> table = new TableView<>();
    private final ObservableList<Entry> data = readDataFromFile();

    public M3UEditor() throws FileNotFoundException {
    }

    public static void main(String[] args) {
        launch(args);
    }

    private ObservableList<Entry> readDataFromFile() throws FileNotFoundException {
        ObservableList<Entry> dataFromFile = FXCollections.observableArrayList();
        Parser parser = new Parser();
        File initialFile = new File("/home/jochen/projekte/m3uedit/src/test/resources/tvsd.m3u");
        InputStream inputStream = new DataInputStream(new FileInputStream(initialFile));
        PlayList<Entry> playList = parser.parse(inputStream);
        dataFromFile.addAll(playList);
        return dataFromFile;
    }

    @Override
    public void start(Stage primaryStage) throws FileNotFoundException {
        Scene scene = new Scene(new Group());
        table.setPrefWidth(600);
        primaryStage.setTitle("Table View Sample");
        primaryStage.setWidth(640);
        primaryStage.setHeight(550);

        final Label label = new Label("Channel list");
        label.setFont(new Font("Arial", 20));

        table.setEditable(true);
        // Channel Name
        TableColumn<Entry, String> channelName = new TableColumn<>("Channel Name");
        channelName.setMinWidth(100);
        channelName.setCellValueFactory(new PropertyValueFactory<>("channelName"));

        channelName.setCellFactory(TextFieldTableCell.forTableColumn());
        channelName.setOnEditCommit(
                t -> (t.getTableView().getItems().get(
                        t.getTablePosition().getRow())
                ).setChannelName(t.getNewValue())
        );

        // Channel URI
        TableColumn<Entry, String> channelUri = new TableColumn<>("Channel URI");
        channelUri.setMinWidth(100);
        channelUri.setCellValueFactory(new PropertyValueFactory<>("channelUri"));

        channelUri.setCellFactory(TextFieldTableCell.forTableColumn());
        channelUri.setOnEditCommit(
                t -> (t.getTableView().getItems().get(
                        t.getTablePosition().getRow())
                ).setChannelUri(t.getNewValue())
        );

        // set RowFactory to make rows draggable

        table.setRowFactory(tv -> {
            TableRow<Entry> row = new TableRow<>();

            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Integer index = row.getIndex();
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    db.setDragView(row.snapshot(null, null));
                    ClipboardContent cc = new ClipboardContent();
                    cc.put(SERIALIZED_MIME_TYPE, index);
                    db.setContent(cc);
                    event.consume();
                }
            });

            row.setOnDragOver(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasContent(SERIALIZED_MIME_TYPE) && (row.getIndex() != (Integer) db.getContent(SERIALIZED_MIME_TYPE))) {
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                    event.consume();

                }
            });

            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                    int draggedIndex = (Integer) db.getContent(SERIALIZED_MIME_TYPE);
                    Entry draggedEntry = table.getItems().remove(draggedIndex);

                    int dropIndex;

                    if (row.isEmpty()) {
                        dropIndex = table.getItems().size();
                    } else {
                        dropIndex = row.getIndex();
                    }

                    table.getItems().add(dropIndex, draggedEntry);

                    event.setDropCompleted(true);
                    table.getSelectionModel().select(dropIndex);
                    event.consume();
                }
            });

            return row;
        });

        table.setItems(readDataFromFile());

        table.getColumns().addAll(channelName, channelUri);

        final TextField addChannelName = new TextField();
        addChannelName.setPromptText("Channel Name");
        addChannelName.setMaxWidth(channelName.getPrefWidth());
        final TextField addChannelUri = new TextField();
        addChannelUri.setMaxWidth(channelUri.getPrefWidth());
        addChannelUri.setPromptText("Last Name");

        final Button addButton = new Button("Add");
        addButton.setOnAction(e -> {
            Entry entry = new Entry();
            entry.setChannelName(addChannelName.getText());
            entry.setChannelUri(addChannelUri.getText());
            data.add(entry);
            addChannelName.clear();
            addChannelUri.clear();
        });

        hb.getChildren().addAll(addChannelName, addChannelUri, addButton);
        hb.setSpacing(3);

        final VBox vbox = new VBox();
        vbox.setSpacing(5);
        vbox.setPadding(new Insets(10, 0, 0, 10));
        vbox.getChildren().addAll(label, table, hb);

        ((Group) scene.getRoot()).getChildren().addAll(vbox);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

}
