# Development

Building M3UEditor, running it from the IDE, how it is built inside, and how versions and releases are made. See
[CONTRIBUTING.md](../CONTRIBUTING.md) for how to contribute, and [Writing plugins](plugins.md) for how to write a
plugin.

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
  * [The plugin API on Maven Central](#the-plugin-api-on-maven-central)
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
through the API. All modules have the version of the application (`${revision}`) and the groupId `io.github.bit-punk`
(the namespace of the plugin API on Maven Central).

The directory `m3uedit-plugin-template` is not a module: it is the template for plugin authors, a Maven project of its
own building against the published plugin API (see [Writing plugins](plugins.md#from-the-template)). The CI build
builds it against the API of the build.

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

How to write a plugin, build and install it is described for plugin authors in [Writing plugins](plugins.md), along
with the rules of compatibility of the API. The API (`org.bitpunk.m3uedit.api`, module `m3uedit-plugin-api`) only
depends on Java and JavaFX, and does not use Lombok, so its published sources and Javadoc are what plugins call; a test
(`ApiBoundaryTest`) keeps it that way. The module `m3uedit-example-plugin` (a radio filter) is the plugin of another
party the tests of `m3uedit-app` load; the template `m3uedit-plugin-template` (a group filter) is the starting point
for plugin authors.

### Plugin jars

A plugin of another party is a jar installed by the user in `~/.m3uedit/extensions/` (see also
[Packaging the jar](plugins.md#packaging-the-jar)):

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

### The plugin API on Maven Central

Publishing a release (not a pre-release) also publishes the plugin API `io.github.bit-punk:m3uedit-plugin-api` in
the version of the release to Maven Central: jar, sources and Javadoc, signed, with a POM without parent and without
`${revision}` (flatten plugin, mode `ossrh`). The job `plugin-api` of the release workflow first builds the plugin
template against the API, then publishes it (profile `central` of `m3uedit-plugin-api`, the
`central-publishing-maven-plugin`, waiting until Maven Central has published it), and attaches the template with the
version of the release as `m3uedit-plugin-template-<version>.zip`. [javadoc.io](https://javadoc.io/doc/io.github.bit-punk/m3uedit-plugin-api)
shows the Javadoc of each published version by itself.

It needs these secrets of the repository:

| Secret | Content |
|---|---|
| `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` | a user token of [central.sonatype.com](https://central.sonatype.com) for the verified namespace `io.github.bit-punk` |
| `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE` | the GPG key signing the artifacts (armored private key), its public key published on a key server (e.g. `keys.openpgp.org`) |

Maven Central never deletes or replaces a version: a release that has been published cannot be published again with
other content.

**Compatibility of the API:** the build compares the API with the first release of the current `PluginApi.VERSION`
(property `plugin-api.baseline` of `m3uedit-plugin-api/pom.xml`, [japicmp](https://siom79.github.io/japicmp/)) and
fails on changes that are not binary or source compatible. Such a change is only made deliberately: it raises
`PluginApi.VERSION` (and the `m3uedit.plugin-api` of the template, checked by the CI build) and sets
`plugin-api.baseline` to the coming release, so the check starts again from there; the release notes name it. Until
the baseline has been published (e.g. before the first release of a new `PluginApi.VERSION`), the check is skipped
with a warning. Changes of meaning are not found by the check, see the rules in
[Compatibility](plugins.md#compatibility). The report is written to `m3uedit-plugin-api/target/japicmp/`.

After a release, the pull request raising the `revision` also sets `m3uedit.version` of the template to the release
just published, so the template in the repository builds against a published API.

## Links

* [JavaFX](https://openjfx.io/) and its [documentation](https://openjfx.io/openjfx-docs/)
* [M3U](https://en.wikipedia.org/wiki/M3U) and the [XMLTV format](https://github.com/XMLTV/xmltv/blob/master/xmltv.dtd)
* [Fritz DVB to VLC](https://github.com/passing/fritz-dvb-to-vlc): the playlists of a FritzBox
* [Java Service Provider Interface](https://www.baeldung.com/java-spi)
