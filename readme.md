# M3UEditor
Small editor for simple m3u-playlist files.

## Intellij
* https://openjfx.io/openjfx-docs/#introduction
* To be able to run the JavaFX application from Intellij add the following VM options: 
    ```
      --module-path /home/jochen/javafx-sdk-20.0.2/lib --add-modules javafx.controls,javafx.fxml
    ```
## Building
To build the m3uedit binaries you should open a terminal-window, navigate to the directory where you have checked ou the sources and invoke the maven-wrapper:
```bash
./mvnw clean install
```
After a successful build you will find the resulting _m3uedit_ binary under `./target/m3uedit-1.0-SNAPSHOT.jar`

## Links
* https://openjfx.io/
* [JavaFX Downloads](https://gluonhq.com/products/javafx/)
* https://en.wikipedia.org/wiki/M3U
* [Oracle TableView example](https://docs.oracle.com/javafx/2/ui_controls/table-view.htm)
* https://stackoverflow.com/questions/28603224/sort-tableview-with-drag-and-drop-rows
* [JavaFX FXML](https://jenkov.com/tutorials/javafx/fxml.html)
* https://stackoverflow.com/questions/23687836/javafx-tableview-resize-to-fit-window
* [Implementing Plugins with Java’s Service Provider Interface](https://reflectoring.io/service-provider-interface/)
* [Baeldung: ava Service Provider Interface](https://www.baeldung.com/java-spi)
* [Class ServiceLoader](https://docs.oracle.com/javase/6/docs/api/java/util/ServiceLoader.html)
* [Fritz DVB to VLC](https://github.com/passing/fritz-dvb-to-vlc)
