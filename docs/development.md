# Development

Building M3UEditor, running it from the IDE, how it is built inside, and how versions and releases are made. See
[CONTRIBUTING.md](../CONTRIBUTING.md) for how to contribute.

* [Building](#building)
* [Running from the IDE](#running-from-the-ide)
* [Tests](#tests)
* [Architecture](#architecture)
  * [Modules](#modules)
  * [Packages](#packages)
  * [The pipeline](#the-pipeline)
  * [Manual changes and undo](#manual-changes-and-undo)
  * [Plugins](#plugins)
  * [Writing a plugin](#writing-a-plugin)
  * [Plugin jars](#plugin-jars)
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

After a successful build you will find the resulting jar under `./m3uedit-app/target/m3uedit-<version>.jar` (e.g.
`m3uedit-1.2.0-SNAPSHOT.jar`, see [Versions and releases](#versions-and-releases)), which can be started using
`java -jar m3uedit-app/target/m3uedit-<version>.jar`. The jar contains the plugin API, the built-in plugins and the JavaFX libraries of the platform it has been built
on, so it only runs on that platform.

To create a self-contained application image for your current platform (including a java runtime, no separate JDK
or JavaFX installation needed), use the `jpackage` profile:

```bash
./mvnw -Pjpackage package
```

The application can then be started using `./m3uedit-app/target/dist/m3uedit/bin/m3uedit` (Linux) or the corresponding launcher
on Windows/macOS.

## Running from the IDE

Create a run-configuration for the class `org.bitpunk.m3uedit.Main`. It does not extend
`javafx.application.Application`, so the JavaFX libraries can be used from the classpath and no module path is
needed. Without arguments it starts the user interface, with arguments the [command-line mode](command-line.md).

## Tests

* `./mvnw package` runs all tests of all modules.
* The tests of the built-in plugins are part of `m3uedit-app`, in the packages of the plugins: they use the context
  and the project-files of the application, which `m3uedit-plugins` cannot depend on. They create plugins with
  `testsupport.TestPluginSupport`, initialised with their context.
* Tests use a temporary configuration directory instead of `~/.m3uedit`
  (see `m3uedit-app/src/test/java/org/bitpunk/m3uedit/testsupport/TestConfigDirListener.java`), in Maven as well as in
  the IDE.
* Some tests show windows, so on a machine without display run them using `xvfb-run ./mvnw package`.
* Tests run in English, whatever the language of the system (see [Texts and languages](#texts-and-languages)).

## Architecture

### Modules

The build has three Maven modules, with the parent POM in the root directory:

| Module | Content | Depends on |
|---|---|---|
| `m3uedit-plugin-api` | the plugin API, the package `org.bitpunk.m3uedit.api` | JavaFX (provided) |
| `m3uedit-plugins` | the built-in plugins, their dialogs, texts, default configuration files and `META-INF/services` | the plugin API only (and libraries of their own, e.g. JSch) |
| `m3uedit-example-plugin` | an example of a plugin of another party (a radio filter), built as jar of its own; the tests of `m3uedit-app` load it like an installed jar | the plugin API (provided) |
| `m3uedit-app` | the application (artifact `m3uedit`), the shaded jar and the application images | the plugin API; the built-in plugins only at runtime |

So the compiler ensures that the built-in plugins get along with the API, and that the application uses plugins only
through the API. All modules have the version of the application (`${revision}`).

### Packages

All classes are in `org.bitpunk.m3uedit`:

| Package | Content |
|---|---|
| (root) | `Main` (user interface or command-line mode), `M3UEditor` (the JavaFX application) |
| `api` | the plugin API (module `m3uedit-plugin-api`): the interfaces of the plugins, `Entry`, the context of a plugin, see [Writing a plugin](#writing-a-plugin) |
| `model` | `DataModel` (a project and its pipeline), `ManualChange`, the results of a computation |
| `controller` | the windows and dialogs: main window (`SceneController`), project, pipeline, playlist, diff, programme guide, configuration |
| `control` | reusable controls, e.g. the stages of the pipeline (`PluginStageView`) |
| `plugins` | the plugin services, the context and state of plugins; in the module `m3uedit-plugins` the built-in plugins: `input`, `filter`, `sorter`, `enricher`, `output`, the SFTP support in `sftp`, the dialog support in `support` |
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

Inputs, filters, sorters, enrichers and outputs are plugins written against the plugin API, the package
`org.bitpunk.m3uedit.api` (see [Writing a plugin](#writing-a-plugin)). They are found by the Java
[ServiceLoader](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html) via the
files in `m3uedit-plugins/src/main/resources/META-INF/services` (one file per kind of plugin, named after its interface, e.g.
`org.bitpunk.m3uedit.api.Input`, see the `*PluginService` classes). Registering a plugin there is all that is needed:
the add-menus of the pipeline, new projects and the project-files take the plugins from the plugin services.

The plugin services describe each plugin by a `PluginDescriptor` (id, class, name) and create new instances with
`PluginDescriptor.create()`, which also passes the plugin its context. A plugin with the id of an earlier one is
ignored and logged as an error, as the two could not be told apart in a project-file. Whether a plugin is enabled is
kept by m3uedit (`PluginStates`), not by the plugin.

Plugins of other parties are installed as jars in `~/.m3uedit/extensions/` (`PluginJars`), see
[Plugin jars](#plugin-jars). A plugin of a project-file that is not installed is kept as placeholder
(`plugins.missing.MissingPlugin`, one class per kind): its type id, whether it is enabled and its settings are kept as
data (no class is loaded for it) and written back unchanged; it does not take part in the computation.

### Writing a plugin

The API (`org.bitpunk.m3uedit.api`, module `m3uedit-plugin-api`) only depends on Java and JavaFX; a test
(`ApiBoundaryTest`) keeps it that way.

| Type | Purpose |
|---|---|
| `Input`, `Filter`, `Sorter`, `Enricher`, `Output` | the kinds of plugins, all extending `Plugin` |
| `@PluginType` | the type id, the key of the name and the resource bundle of the texts |
| `PluginContext` | what a plugin gets from m3uedit: its texts (also as resource bundle for the FXML files of its dialogs), reading and writing M3U playlists, its configuration files, whether it runs without user interface, showing errors |
| `CollectedData` | data a plugin collects (e.g. recently used servers, downloaded files), shown in the configuration dialog of m3uedit, where the user can clear it |
| `Entry`, `Playlist` | a channel, and the entries read by an input |
| `Enrichment`, `Attachment`, `Export`, `Programme`, `ProgrammeGuideProvider` | the results of enrichers, what outputs write, programme guides |
| `PluginException`, `InputReadException` | errors shown to the user |

A minimal filter:

```java
@PluginType(id = "de.example.RadioFilter", nameKey = "radioFilter.name", texts = "de.example.messages")
public class RadioFilter implements Filter {
    private PluginContext context;
    private RadioFilterSettings settings = new RadioFilterSettings(true);

    @Override
    public void init(PluginContext context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return context.text("radioFilter.name");
    }

    @Override
    public String getDescription() {
        return context.text("radioFilter.description");
    }

    @Override
    public List<Entry> filter(List<Entry> entries) {
        return entries.stream().filter(entry -> !settings.removeRadio() || !"true".equals(entry.getRadio())).toList();
    }

    @Override
    public Class<RadioFilterSettings> getSettingsType() {
        return RadioFilterSettings.class;
    }

    @Override
    public RadioFilterSettings getSettings() {
        return settings;
    }

    @Override
    public void setSettings(Record settings) {
        this.settings = (RadioFilterSettings) settings;
    }
}

public record RadioFilterSettings(boolean removeRadio) {
}
```

registered in `META-INF/services/org.bitpunk.m3uedit.api.Filter` by the line `de.example.RadioFilter`. The module
`m3uedit-example-plugin` is a complete example, with settings, texts in English and German and its build.

* **Identity:** `@PluginType(id = …)` is the type id stored in project-files. It must never change once project-files
  have been written with it, also when the class is renamed or moved. The plugins of m3uedit use the simple class
  names they have always been stored with; plugins of other parties use qualified ids. `nameKey` lets the add-menus
  name the plugin without creating it. Plugins are compared by identity, so they must not override `equals`.
* **Context:** m3uedit creates a plugin for each use in a project and calls `init(PluginContext)` once; the plugin
  keeps the context. Its texts (`context.text(key, …)`) come from its own resource bundle (`texts`, e.g.
  `de/example/messages.properties` and `messages_de.properties`) in the language of the user interface; the built-in
  plugins use the bundle of m3uedit.
* **Settings:** the settings stored in the project-file are a record of plain Java types, without annotations. m3uedit
  writes its components next to the type of the plugin and whether it is enabled, and passes them back with
  `setSettings` when the project is loaded; components missing in an older project-file are `null`, `0` or `false`, so
  the plugin replaces them by its defaults. A plugin without settings keeps the defaults (`NoSettings`). Configuration
  that belongs to the user rather than to a project (e.g. lists of recently used servers) is kept in configuration
  files with `context.loadConfig` / `context.saveConfig` in `~/.m3uedit/plugins/`.
* **Dialogs:** a plugin builds its own dialogs with JavaFX; the colours of the theme of m3uedit are available as
  looked-up colours (e.g. `-app-error`, `-app-invalid-background`, see `theme.css`), so the dialogs look right in the
  light and the dark theme.
* **Threads:** reading, filtering, sorting, enriching and writing run in the background and must not use JavaFX;
  they also run in the command-line mode (`context.isHeadless()`). `configure(Window)` opens the plugin's own dialog on
  the JavaFX application thread and returns whether the user confirmed.

### Plugin jars

A plugin of another party is a jar installed by the user in `~/.m3uedit/extensions/`:

* It contains the plugin, its `META-INF/services/org.bitpunk.m3uedit.api.<Kind>` files, its texts and resources, and
  the libraries it needs (e.g. a shaded jar). It does not contain the plugin API, JavaFX or libraries of m3uedit:
  each jar gets a class loader of its own with m3uedit as parent, which is asked first, so these are always the
  classes of m3uedit. A plugin needing another version of a library of m3uedit has to relocate it when shading.
* Its manifest declares the version of the plugin API it was built for, `M3UEdit-Plugin-API: 1`
  (`PluginApi.VERSION`, raised with every incompatible change of the API); m3uedit only loads jars built for its
  version. With Maven, e.g. `<manifestEntries><M3UEdit-Plugin-API>1</M3UEdit-Plugin-API></manifestEntries>` in the
  configuration of the `maven-jar-plugin` (the example plugin uses a `MANIFEST.MF` of its resources).
* Its plugins use qualified type ids (e.g. `de.example.RadioFilter`); a plugin with the id of a built-in plugin, or of
  a plugin of a jar loaded before (jars are loaded in the order of their names), is not loaded.

Jars that cannot be loaded, and plugins in them that cannot be loaded or created, are skipped with the reason
(`PluginJars.getProblems()`), shown by *Help → Plugins* and `--list-plugins`.

### Project-files

Projects are stored as YAML by Jackson (`util.ProjectPersisterUtil`), mapping the fields of the model (not their
getters); `transient` and `@JsonIgnore` fields are not stored. Each plugin is stored by `jackson.PluginModule` with the
type id of its `@PluginType`, whether it is enabled and the components of its settings record (e.g.
`type: FileInput`, `enabled: true`, `source: …`); only the plugins found by the plugin services of the expected kind
are accepted when loading a project, and the settings are read as the settings record of the plugin, so a project-file
cannot create other objects. The entries themselves are not stored, they are read from the inputs when a project is opened.

### Texts and languages

The texts of the user interface are kept in `m3uedit-app/src/main/resources/i18n/messages.properties` (English) and
`messages_de.properties` (German) and read by `org.bitpunk.m3uedit.i18n.Messages`; FXML files refer to them as
`%key`. The built-in plugins have texts of their own, `m3uedit-plugins/src/main/resources/org/bitpunk/m3uedit/plugins/
messages*.properties`, read through their context. Texts with arguments (`{0}`) are formatted by `MessageFormat`, so an
apostrophe is written as `''` there. `MessagesTest` and `PluginMessagesTest` check that both languages contain the same
keys and arguments and that every key used exists. The
command-line mode, the log and the project-files stay in English: the language is only set when the user interface
is started.

### Theme

The colours are defined in `m3uedit-app/src/main/resources/fxml/css/theme.css`, which `org.bitpunk.m3uedit.util.Themes` adds to
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
