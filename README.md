![maven workflow](https://github.com/bit-punk/m3uedit/actions/workflows/maven.yml/badge.svg)

# M3UEditor

M3UEditor is a small editor written in Java based on JavaFX for managing m3u-playlist files.

* load m3u-playlists from various sources (File, Fritzbox)
* automatically filter the playlist using the build in Filters
* sort entries of your playlist using build-in sorters
* save the resulting playlist to various output-channels

## Prerequisites

* JDK 21
* Linux, Windows, MacOS

## Building

To build the m3uedit binaries you should open a terminal-window, navigate to the directory where you have checked ou the
sources and invoke the [maven-wrapper](https://maven.apache.org/wrapper/):

```bash
./mvnw clean install
```

To run the application using the _javafx-maven-plugin_ you should use:

```bash
./mvnw javafx:run
```

After a successful build you will find the resulting _m3uedit_ binary under `./target/m3uedit-1.0-SNAPSHOT.jar`

## Developing

### GitFlow

[gitflow-maven-plugin](https://github.com/aleksandr-m/gitflow-maven-plugin)

```bash
./mvnw gitflow:feature-start
```

### IntelliJ

* To be able to run the JavaFX application from IntelliJ add the following VM options of your run-configuration:
    ```
      --module-path /home/jochen/javafx-sdk-20.0.2/lib --add-modules javafx.controls,javafx.fxml
    ```

### Links

* [JavaFX official Homepage](https://openjfx.io/)
* [JavaFX Downloads](https://gluonhq.com/products/javafx/)
* [Getting Started with JavaFX](https://openjfx.io/openjfx-docs/#introduction)
* [Jenkov.com - JavaFX FXML](https://jenkov.com/tutorials/javafx/fxml.html)
* [Wikipedia M3U](https://en.wikipedia.org/wiki/M3U)
* [Oracle TableView example](https://docs.oracle.com/javafx/2/ui_controls/table-view.htm)
* [sort tableview with drag and drop (rows)](https://stackoverflow.com/questions/28603224/sort-tableview-with-drag-and-drop-rows)
* [JavaFX tableview resize to fit window](https://stackoverflow.com/questions/23687836/javafx-tableview-resize-to-fit-window)
* [Implementing Plugins with Java’s Service Provider Interface](https://reflectoring.io/service-provider-interface/)
* [Baeldung: Java Service Provider Interface](https://www.baeldung.com/java-spi)
* [Class ServiceLoader](https://docs.oracle.com/javase/6/docs/api/java/util/ServiceLoader.html)
* [Fritz DVB to VLC](https://github.com/passing/fritz-dvb-to-vlc)

#### Creating JavaFX Custom-Dialogs

* [JavaFX Dialogs (official)](https://code.makery.ch/blog/javafx-dialogs-official/)
* [JavaFX Dialog Example](https://examples.javacodegeeks.com/java-development/desktop-java/javafx/dialog-javafx/javafx-dialog-example/)
* [Accessing the OK button from inside the dialog controller - javaFX](https://stackoverflow.com/questions/49072726/accessing-the-ok-button-from-inside-the-dialog-controller-javafx)
* [Java FX create Custom Dialog with fxml file. How to set or get result from it?](https://stackoverflow.com/questions/64964471/java-fx-create-custom-dialog-with-fxml-file-how-to-set-or-get-result-from-it)
* [Creating a Custom Control with FXML](https://docs.oracle.com/javafx/2/fxml_get_started/custom_control.htm#BABDAAHE)

#### MVC in JavaFx

* [Applying MVC With JavaFx](https://stackoverflow.com/questions/32342864/applying-mvc-with-javafx)
* [How to apply MVC in JavaFX](https://edencoding.com/mvc-in-javafx/)

#### Packaging JavaFX applications

* [JavaFX, JLink and JPackage](https://dev.to/cherrychain/javafx-jlink-and-jpackage-h9)
* [Creating Fat JARs for JavaFX Applications](http://fxapps.blogspot.com/2020/11/creating-fat-jars-for-javafx.html)
* [JavaFX 11 : Create a jar file with Gradle](https://stackoverflow.com/questions/52569724/javafx-11-create-a-jar-file-with-gradle)

#### SFTP-Output

* [com.jcraft.jsch.JSchException: UnknownHostKey](https://stackoverflow.com/questions/2003419/com-jcraft-jsch-jschexception-unknownhostkey)

#### JSR303

* [JSR 303 Annotations List](https://howtodoinjava.com/spring-mvc/spring-bean-validation-example-with-jsr-303-annotations/#6-jsr-303-annotations-list)

#### TableView validation

* [ListView validate edit and prevent commit](https://stackoverflow.com/questions/35281105/listview-validate-edit-and-prevent-commit)
* [JavaFX 8 TableView Cell Renderer](https://code.makery.ch/blog/javafx-8-tableview-cell-renderer/)