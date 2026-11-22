# Development

Building M3UEditor, running it from the IDE, how it is built inside, and how versions and releases are made. See
[CONTRIBUTING.md](../CONTRIBUTING.md) for how to contribute.

* [Building](#building)
* [Running from the IDE](#running-from-the-ide)
* [Tests](#tests)
* [Architecture](#architecture)
  * [Packages](#packages)
  * [The pipeline](#the-pipeline)
  * [Manual changes and undo](#manual-changes-and-undo)
  * [Plugins](#plugins)
  * [Project-files](#project-files)
  * [Texts and languages](#texts-and-languages)
  * [Theme](#theme)
* [Versions and releases](#versions-and-releases)
* [Links](#links)

## Building

Prerequisites:

* JDK 25+
* Linux, Windows, macOS

JavaFX is pulled in by Maven, a separate JavaFX SDK is not needed.

To build m3uedit, open a terminal, navigate to the directory where you have checked out the sources and invoke the
[maven-wrapper](https://maven.apache.org/wrapper/):

```bash
./mvnw clean install
```

To run the application using the _javafx-maven-plugin_, use:

```bash
./mvnw javafx:run
```

After a successful build you will find the resulting jar under `./target/m3uedit-<version>.jar` (e.g.
`m3uedit-1.2.0-SNAPSHOT.jar`, see [Versions and releases](#versions-and-releases)), which can be started using
`java -jar target/m3uedit-<version>.jar`. The jar contains the JavaFX libraries of the platform it has been built
on, so it only runs on that platform.

To create a self-contained application image for your current platform (including a java runtime, no separate JDK
or JavaFX installation needed), use the `jpackage` profile:

```bash
./mvnw -Pjpackage package
```

The application can then be started using `./target/dist/m3uedit/bin/m3uedit` (Linux) or the corresponding launcher
on Windows/macOS.

## Running from the IDE

Create a run-configuration for the class `org.bitpunk.m3uedit.Main`. It does not extend
`javafx.application.Application`, so the JavaFX libraries can be used from the classpath and no module path is
needed. Without arguments it starts the user interface, with arguments the [command-line mode](command-line.md).

## Tests

* `./mvnw package` runs all tests.
* Tests use a temporary configuration directory instead of `~/.m3uedit`
  (see `src/test/java/org/bitpunk/m3uedit/testsupport/TestConfigDirListener.java`), in Maven as well as in the IDE.
* Some tests show windows, so on a machine without display run them using `xvfb-run ./mvnw package`.
* Tests run in English, whatever the language of the system (see [Texts and languages](#texts-and-languages)).

## Architecture

### Packages

All classes are in `org.bitpunk.m3uedit`:

| Package | Content |
|---|---|
| (root) | `Main` (user interface or command-line mode), `M3UEditor` (the JavaFX application) |
| `model` | `DataModel` (a project and its pipeline), `Entry`, `ManualChange`, the results of a computation |
| `controller` | the windows and dialogs: main window (`SceneController`), project, pipeline, playlist, diff, programme guide, configuration |
| `control` | reusable controls, e.g. the stages of the pipeline (`PluginStageView`) |
| `plugins` | the plugin interfaces and the plugins: `input`, `filter`, `sorter`, `enricher`, `output`, the SFTP support in `sftp` |
| `parser` | reading and writing M3U playlists |
| `cli` | the command-line mode |
| `config` | the configuration files in `~/.m3uedit` and the settings |
| `i18n` | the texts of the user interface (`Messages`) |
| `player` | the external player |
| `util` | everything else, e.g. persisting projects, dialogs, theme, icons |

### The pipeline

A project is a `DataModel`. It computes the playlist in steps, see the diagram in the
[user guide](user-guide.md#the-pipeline):

1. read the entries of all inputs (`raw`),
2. apply the enabled filters in their order (`filtered`),
3. apply the enabled sorter (`sorted`),
4. apply the enabled enrichers (`enriched`), which may also provide files (attachments) and EPG URLs,
5. apply the manual changes, giving the playlist (`sortedEntryList`).

The results of steps 1–4 are kept in a `PipelineResult`, together with the result of each plugin (`StepResult`), the
input each entry has been read from, the filter that removed an entry and the entry an enriched one has been derived
from; the diff, the badges of the pipeline and the summary are shown from it.

A change re-computes only from the step affected on (`reloadInputs`, `filterRawEntries`, `sortFilteredEntries`,
`enrichSortedEntries`): the computation runs on a background executor, its result is applied on the JavaFX thread. A
computation still running is superseded by a new one. Inputs that cannot be read are reported per input
(`InputReadException`), the other inputs are used anyway.

Outputs write the playlist and the attachments when *Write playlist* is used; the command-line mode
(`cli.CommandLineRunner`) runs the same pipeline without user interface.

### Manual changes and undo

Changes made by hand are not applied to a copy of the playlist, but kept as a list of `ManualChange`s (add, remove,
edit, move), identifying entries by value (`EntryKey`). They are applied to the result of the enrichers after each
computation (`ManualChange.applyAll`), so they survive reading the inputs again, and are stored in the project-file.

Undo and redo keep the list of manual changes before and after each step; `DataModel.runAsOneStep` groups changes of
several entries into one step.

### Plugins

Inputs, filters, sorters, enrichers and outputs are plugins, found by the Java
[ServiceLoader](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html) via the
files in `src/main/resources/META-INF/services` (one file per plugin interface, see the `*PluginService` classes).
Plugins with settings of their own keep them in `~/.m3uedit/plugins/`. External plugins in separate jars are
planned, see [#46](https://github.com/bit-punk/m3uedit/issues/46).

### Project-files

Projects are stored as YAML by Jackson (`util.ProjectPersisterUtil`), mapping the fields of the model and the
plugins (not their getters); `transient` and `@JsonIgnore` fields are not stored. Each plugin is stored with its simple
class name as type (e.g. `type: FileInput`); only the plugins found by the plugin services are accepted when loading a
project. The entries themselves are not stored, they are read from the inputs when a project is opened.

### Texts and languages

The texts of the user interface are kept in `src/main/resources/i18n/messages.properties` (English) and
`messages_de.properties` (German) and read by `org.bitpunk.m3uedit.i18n.Messages`; FXML files refer to them as
`%key`. Texts with arguments (`{0}`) are formatted by `MessageFormat`, so an apostrophe is written as `''` there.
`MessagesTest` checks that both languages contain the same keys and arguments and that every key used exists. The
command-line mode, the log and the project-files stay in English: the language is only set when the user interface
is started.

### Theme

The colours are defined in `src/main/resources/fxml/css/theme.css`, which `org.bitpunk.m3uedit.util.Themes` adds to
every window; the dark theme adds the style class `dark` to the root of each window. Stylesheets use the variables
of `theme.css` (e.g. `-app-muted`, `-app-error`) instead of fixed colours, and switch their own colours with
`.dark .their-class` rules.

## Versions and releases

The version is defined once, by the release on GitHub: building a release (`.github/workflows/release.yml`) sets
the version of its tag (`-Drevision=1.2.0`) for the jar, the application image and the about dialog. Local builds
use the `revision` of the `pom.xml`, the next release as snapshot (e.g. `1.2.0-SNAPSHOT`); it only needs to be
raised by a pull request when a release has been published.

Release Drafter (`.github/release-drafter.yml`) collects the pull requests merged into `develop` in a draft release,
sorted by their labels, which are set from the names of their branches (`feature/`, `fix/`, `chore/`). Publishing
the draft builds the application images for Linux, Windows and macOS and attaches them to the release.

## Links

* [JavaFX](https://openjfx.io/) and its [documentation](https://openjfx.io/openjfx-docs/)
* [M3U](https://en.wikipedia.org/wiki/M3U) and the [XMLTV format](https://github.com/XMLTV/xmltv/blob/master/xmltv.dtd)
* [Fritz DVB to VLC](https://github.com/passing/fritz-dvb-to-vlc): the playlists of a FritzBox
* [Java Service Provider Interface](https://www.baeldung.com/java-spi)
