package org.bitpunk.m3uedit;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.bitpunk.m3uedit.model.Entry;
import org.bitpunk.m3uedit.model.PlayList;
import org.bitpunk.m3uedit.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class M3UEditor extends Application {
    private static final Logger log = LoggerFactory.getLogger(M3UEditor.class);
    int i = 3;

    public static void main(String[] args) {
        launch(args);
    }

    private List<String> getChannelNames() throws FileNotFoundException {
        List<String> names = new ArrayList<>();
        Parser parser = new Parser();
        File initialFile = new File("/home/jochen/projekte/m3uedit/src/test/resources/tvsd.m3u");
        InputStream inputStream = new DataInputStream(new FileInputStream(initialFile));
        PlayList<Entry> playList = parser.parse(inputStream);
        playList.forEach(entry-> names.add(entry.getChannelName()));
        return names;
    }

    @Override
    public void start(Stage primaryStage) throws FileNotFoundException {
        ObservableList<String> items = FXCollections.observableArrayList(getChannelNames());
        ListView<String> list = new ListView<>(items);
        list.setCellFactory(TextFieldListCell.forListView());
        list.setEditable(true);

        Button btn = new Button();
        btn.setText("Add String");
        btn.setOnAction((ActionEvent event) -> {
            list.getItems().add(i - 1, "test" + i);
            list.edit(i - 2);
            i++;
        });

        VBox root = new VBox(list, btn);

        Scene scene = new Scene(root);

        primaryStage.setTitle("test!");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

}
