![maven workflow](https://github.com/bit-punk/m3uedit/actions/workflows/maven.yml/badge.svg)

# M3UEditor

M3UEditor is a small editor written in Java based on JavaFX for managing m3u-playlist files.

* load m3u-playlists from various sources: local files, URLs, a FritzBox, [iptv-org](https://github.com/iptv-org/iptv)
  (by country, category, language, region or single channels) and SFTP servers
* automatically filter the playlist using the built-in filters: channel names (regular expressions), SD channels,
  duplicate names, duplicate URLs and unreachable URLs
* sort the entries of your playlist alphabetically or by a preferred channel order (custom profiles can be created
  from existing playlists)
* see the whole pipeline at a glance: inputs, filters, sorters and outputs, each with the number of entries it
  removed or moved; filters and sorters are enabled and reordered right there
* compare the entries before and after each step in a side-by-side diff, including the reason why a filter removed
  an entry (e.g. `"ZDF HD" exists` or `same URL as "Das Erste HD"`)
* edit, add, remove and reorder single entries of the playlist and play them in an external player (vlc by default)
* save the resulting playlist to a local file or an SFTP server
* add a programme guide (EPG) with the EPG Enricher: it assigns the entries to the channels of XMLTV sources (e.g.
  [epgshare01](https://epgshare01.online)) by setting their `tvg-id` - matched by `tvg-id`, URL rules (regular
  expressions on the stream URL), name or the first words of the name - and writes a guide containing only the
  channels of the playlist next to it (`tv.m3u` &rarr; `tv.xml.gz`), referenced by the playlist header; sources are
  cached in `~/.m3uedit/cache/epg`. Without enricher, the EPG URLs of the inputs (`x-tvg-url` / `url-tvg`) are kept,
  or replaced by the URLs set for the project
* save your inputs, filters, sorters and outputs as project (`*.m3uproj`) to re-create the playlist later on

## Prerequisites

* JDK 21+
* Linux, Windows, macOS

JavaFX is pulled in by Maven, a separate JavaFX SDK is not needed.

## Building

To build the m3uedit binaries, you should open a terminal-window, navigate to the directory where you have checked out the
sources and invoke the [maven-wrapper](https://maven.apache.org/wrapper/):

```bash
./mvnw clean install
```

To run the application using the _javafx-maven-plugin_, you should use:

```bash
./mvnw javafx:run
```

After a successful build you will find the resulting _m3uedit_ binary under `./target/m3uedit-1.0-SNAPSHOT.jar`, which
can be started using `java -jar target/m3uedit-1.0-SNAPSHOT.jar`. The jar contains the JavaFX libraries of the platform
it has been built on, so it only runs on that platform.

To create a self-contained application image for your current platform (including a java runtime, no separate JDK
or JavaFX installation needed), use the `jpackage` profile:

```bash
./mvnw -Pjpackage package
```

The application can then be started using `./target/dist/m3uedit/bin/m3uedit` (Linux) or the corresponding launcher
on Windows/macOS.

## Configuration

Besides the project files, the configuration is stored in the directory `~/.m3uedit`. The files are created with
default values on first use and can be edited by hand:

| File                                   | Content                                                    |
|----------------------------------------|------------------------------------------------------------|
| `external_player.yaml`                 | command and arguments of the external player (vlc)          |
| `plugins/channel_name_filter.yaml`     | regular expressions of channel names to filter             |
| `plugins/preferred_channel_order.yaml` | profiles with the preferred order of channels              |
| `plugins/reachable_url_filter.yaml`    | connect-timeout used to check whether a stream is reachable |
| `plugins/sftp_recent_servers.yaml`     | recently used SFTP servers (passwords are never stored)    |

A different directory can be used by setting the system property `m3uedit.config.dir`, e.g.
`java -Dm3uedit.config.dir=/tmp/m3uedit -jar target/m3uedit-1.0-SNAPSHOT.jar`.

## Developing

### IntelliJ

* To run the application from IntelliJ, create a run-configuration for the class `org.bitpunk.m3uedit.Main`. It does not
  extend `javafx.application.Application`, so the JavaFX libraries can be used from the classpath and no module path is
  needed.
* Tests use a temporary configuration directory instead of `~/.m3uedit`
  (see `src/test/java/org/bitpunk/m3uedit/testsupport/TestConfigDirListener.java`), in Maven as well as in the IDE.

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