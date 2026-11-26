# Writing plugins

Inputs, filters, sorters, enrichers and outputs of m3uedit are plugins. Plugins of other parties are written against
the plugin API, built as a jar of their own with the usual tools, and installed by copying the jar into
`~/.m3uedit/extensions/`. This guide leads from an empty project to an installed plugin; how users install and
manage plugins is described in the [user guide](user-guide.md#plugins-of-other-parties).

* [Setting up a project](#setting-up-a-project)
  * [From the template](#from-the-template)
  * [Maven](#maven)
  * [Gradle](#gradle)
* [The API in an overview](#the-api-in-an-overview)
* [A plugin step by step](#a-plugin-step-by-step)
  * [1. The kind of plugin](#1-the-kind-of-plugin)
  * [2. Its identity](#2-its-identity)
  * [3. Registering it](#3-registering-it)
  * [4. Texts](#4-texts)
  * [5. Settings](#5-settings)
  * [6. The configuration dialog](#6-the-configuration-dialog)
  * [7. Threads and the command-line mode](#7-threads-and-the-command-line-mode)
  * [8. Configuration files and collected data](#8-configuration-files-and-collected-data)
  * [9. Errors](#9-errors)
* [Packaging the jar](#packaging-the-jar)
* [Testing](#testing)
* [Installing](#installing)
* [Compatibility](#compatibility)

## Setting up a project

The plugin API is published to Maven Central with every release of m3uedit:

| | |
|---|---|
| groupId | `io.github.bit-punk` |
| artifactId | `m3uedit-plugin-api` |
| version | the release of m3uedit, e.g. `1.3.0` (see [Compatibility](#compatibility) which one to choose) |
| Javadoc | [javadoc.io/doc/io.github.bit-punk/m3uedit-plugin-api](https://javadoc.io/doc/io.github.bit-punk/m3uedit-plugin-api) |

m3uedit runs on Java 25 with JavaFX 25, so a plugin is compiled for Java 25 or older. The API, JavaFX and everything
else m3uedit consists of are provided by m3uedit at runtime: they are dependencies of scope `provided` (Maven) or
`compileOnly` (Gradle) and never part of the plugin jar.

The Java package of the API is `org.bitpunk.m3uedit.api`; it does not follow the groupId.

### From the template

The quickest start is the template, a complete plugin (a filter with settings, a configuration dialog, texts in English
and German, a test and the build) that only depends on the published API:

1. Download `m3uedit-plugin-template-<version>.zip` from a [release](https://github.com/bit-punk/m3uedit/releases)
   and unpack it, or copy the directory [`m3uedit-plugin-template`](../m3uedit-plugin-template) of this repository.
2. Change the `groupId` and `artifactId` in `pom.xml`, the package `com.example.m3uedit`, the type id in
   `@PluginType` and the names of the texts (see [Its identity](#2-its-identity)).
3. `./mvnw package` (or `mvn package`) builds `target/<artifactId>-<version>.jar`, ready to be [installed](#installing).

### Maven

```xml
<properties>
    <maven.compiler.release>25</maven.compiler.release>
    <m3uedit.version>1.3.0</m3uedit.version>
</properties>

<dependencies>
    <dependency>
        <groupId>io.github.bit-punk</groupId>
        <artifactId>m3uedit-plugin-api</artifactId>
        <version>${m3uedit.version}</version>
        <scope>provided</scope>
    </dependency>
    <!-- only for plugins with dialogs; javafx-graphics comes with the API -->
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-controls</artifactId>
        <version>25.0.5.1</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-jar-plugin</artifactId>
            <configuration>
                <archive>
                    <manifestEntries>
                        <!-- PluginApi.VERSION of the API built against -->
                        <M3UEdit-Plugin-API>1</M3UEdit-Plugin-API>
                    </manifestEntries>
                </archive>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Gradle

```kotlin
plugins {
    java
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories {
    mavenCentral()
}

// the tests run against the API and JavaFX as well
configurations.testImplementation { extendsFrom(configurations.compileOnly.get()) }

dependencies {
    compileOnly("io.github.bit-punk:m3uedit-plugin-api:1.3.0")
    // only for plugins with dialogs: the JavaFX jars of the platform the build runs on
    val platform = with(System.getProperty("os.name").lowercase()) {
        when {
            contains("win") -> "win"
            contains("mac") -> "mac"
            else -> "linux"
        }
    }
    compileOnly("org.openjfx:javafx-controls:25.0.5.1:$platform")
    compileOnly("org.openjfx:javafx-graphics:25.0.5.1:$platform")
    compileOnly("org.openjfx:javafx-base:25.0.5.1:$platform")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    manifest {
        attributes("M3UEdit-Plugin-API" to "1")
    }
}

tasks.test {
    useJUnitPlatform()
}
```

The [openjfx Gradle plugin](https://github.com/openjfx/javafx-gradle-plugin) is an alternative to choosing the
JavaFX jars of the platform by hand.

## The API in an overview

| Type | Purpose |
|---|---|
| `Input`, `Filter`, `Sorter`, `Enricher`, `Output` | the kinds of plugins, all extending `Plugin` |
| `@PluginType` | the type id, the key of the name and the resource bundle of the texts |
| `PluginContext` | what a plugin gets from m3uedit: its texts (also as resource bundle for the FXML files of its dialogs), reading and writing M3U playlists, its configuration files, whether it runs without user interface, showing errors |
| `NoSettings` | the settings of a plugin without settings |
| `CollectedData` | data a plugin collects (e.g. recently used servers, downloaded files), shown in the configuration dialog of m3uedit, where the user can clear it |
| `Entry`, `Playlist` | a channel, and the entries read by an input |
| `Enrichment`, `Attachment`, `Export`, `Programme`, `ProgrammeGuideProvider` | the results of enrichers, what outputs write, programme guides |
| `PluginException`, `InputReadException` | errors shown to the user |
| `PluginApi` | the version of the API and the name of the manifest attribute |

The [Javadoc](https://javadoc.io/doc/io.github.bit-punk/m3uedit-plugin-api) describes each method, in particular on
which thread it is called and what it may change.

How the pipeline uses the plugins, in short: the inputs are read and their entries merged; the enabled filters remove
entries; the enabled sorter (at most one) orders them; the enabled enrichers complete them (e.g. the `tvg-id`) and may
provide additional files such as a programme guide; the outputs write the playlist and these files. The
[user guide](user-guide.md#the-pipeline) shows it as a diagram.

## A plugin step by step

The examples are taken from the template, a filter removing the entries of chosen groups (`group-title`).

### 1. The kind of plugin

A plugin implements the interface of its kind: `Input` (`read()`), `Filter` (`filter(entries)`), `Sorter`
(`sort(entries)`), `Enricher` (`enrich(entries)`) or `Output` (`write(export)`), and from `Plugin` at least
`getName()` and `getDescription()`. It needs a public constructor without arguments; m3uedit creates an instance for
each use in a project.

```java
public class GroupFilter implements Filter {

    @Override
    public List<Entry> filter(List<Entry> entries) {
        // the entries kept, in their order: the same objects, the list itself is not changed
    }
}
```

The rules of each kind are in its Javadoc, e.g. a filter only removes entries, a sorter only reorders them, an
enricher returns changed entries as copies (`Entry.copy()`) and never changes their name or stream URL. Plugins are
compared by identity, so they must not override `equals` and `hashCode`.

### 2. Its identity

```java
@PluginType(id = "com.example.m3uedit.GroupFilter", nameKey = "groupFilter.name",
        texts = "com.example.m3uedit.messages")
public class GroupFilter implements Filter {
```

* `id` is the type id stored in the project-files of the users. **It must never change** once the plugin is used,
  also not when the class is renamed or moved: a project-file with another id would not find the plugin any more.
  Use a qualified id of your own (e.g. your package and the name of the plugin); a plugin with the id of a built-in
  plugin or of a plugin loaded before is not loaded.
* `nameKey` is the key of its name in its texts, so m3uedit can name it (e.g. in the add-menus) without creating it.
* `texts` is the base name of its resource bundle, see [Texts](#4-texts).

### 3. Registering it

m3uedit finds plugins with the Java [ServiceLoader](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html):
a file `META-INF/services/org.bitpunk.m3uedit.api.<Kind>` in the resources lists the classes of that kind, one per
line, e.g. `src/main/resources/META-INF/services/org.bitpunk.m3uedit.api.Filter`:

```
com.example.m3uedit.GroupFilter
```

A jar may contain several plugins, also of different kinds.

### 4. Texts

The texts shown to the user are in a resource bundle of the plugin, `com/example/m3uedit/messages.properties`
(English, the default) and `messages_de.properties` (German), both with the same keys. m3uedit passes the plugin a
`PluginContext` once after creating it; the plugin keeps it and asks it for its texts in the language of the user
interface:

```java
@Override
public void init(PluginContext context) {
    this.context = context;
}

@Override
public String getName() {
    return context.text("groupFilter.name");
}

@Override
public String getDescription() {
    return context.text("groupFilter.description", settings.groups().size());
}
```

Texts with arguments (`{0}`) are formatted by `java.text.MessageFormat`, so an apostrophe is written as `''` there.
`context.resourceBundle()` gives the bundle itself, e.g. for the `%key` references of FXML files. The command-line
mode always uses English.

### 5. Settings

What the user configures for one use of the plugin in a project is its settings: a record of plain Java types
(strings, numbers, booleans, enums, lists, maps and records of these), without annotations. m3uedit stores its
components in the project-file next to the type id, and passes them back when the project is opened:

```java
public record GroupFilterSettings(List<String> groups, Boolean keepGroups) {
    public GroupFilterSettings {
        groups = groups == null ? List.of() : List.copyOf(groups);
        keepGroups = keepGroups != null && keepGroups;
    }
}
```

```java
@Override
public Class<GroupFilterSettings> getSettingsType() {
    return GroupFilterSettings.class;
}

@Override
public GroupFilterSettings getSettings() {
    return settings;
}

@Override
public void setSettings(Record settings) {
    this.settings = (GroupFilterSettings) settings;
}
```

In a project-file:

```yaml
filters:
  - type: com.example.m3uedit.GroupFilter
    enabled: true
    groups:
      - Sports
    keepGroups: false
```

Components missing in a project-file (e.g. written by an older version of the plugin) are `null`, `0` or `false`, so
the plugin replaces them by its defaults; wrapper types (`Boolean`, `Integer`) tell "missing" from `false` and `0`.
Never rename or remove a component that has been stored: add new ones instead. A plugin without settings leaves out
these methods (`NoSettings`). Whether the plugin is enabled is managed by m3uedit.

Passwords do not belong into the settings: project-files are plain text and are often shared.

### 6. The configuration dialog

A plugin the user configures returns `true` from `isConfigurable()` (inputs and outputs do by default) and opens its
own dialog in `configure(Window owner)`, built with JavaFX:

```java
@Override
public boolean configure(Window owner) {
    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.initOwner(owner);
    dialog.setTitle(context.text("groupFilter.dialog.title"));
    // ...
    if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
        return false;     // cancelled: the settings are unchanged
    }
    settings = new GroupFilterSettings(groups, keep);
    return true;          // confirmed: m3uedit computes the playlist again
}
```

It is called on the JavaFX application thread, never in the command-line mode. The colours of the theme of m3uedit
are available as looked-up colours in stylesheets (e.g. `-app-error`, `-app-muted`, `-app-invalid-background`), so a
dialog using them looks right in the light and the dark theme.

### 7. Threads and the command-line mode

Reading, filtering, sorting, enriching and writing run in the background: they may block (e.g. on the network), but
must not use JavaFX controls. They also run in the command-line mode (`m3uedit --run`, e.g. by a cron job), where
`context.isHeadless()` is true and no dialog may be opened. `describeRemoval` of filters and `describeChange` of
enrichers are called on the JavaFX application thread and must not block.

### 8. Configuration files and collected data

Configuration that belongs to the user rather than to a project (e.g. a list of servers) is kept in a YAML file in
`~/.m3uedit/plugins/` with `context.loadConfig(fileName, type, defaults)` and `context.saveConfig(fileName, config)`;
the user may also edit it by hand. Files the plugin downloads or computes and can create again go to
`context.cacheDirectory()`. Data the user may want to clear (e.g. recently used servers, downloaded files) is offered
by `getCollectedData()`, shown in the configuration dialog of m3uedit. Use names of files and ids of collected data
that are unique to your plugin (e.g. starting with its package).

### 9. Errors

* An input that cannot be read throws an `IOException` (its message is shown to the user); an input of which only a
  part could be read throws an `InputReadException` with the entries read. The other inputs are used anyway.
* An output that cannot write throws a `PluginException` with a message for the user.
* `context.showError(message)` shows an error in a dialog (in the command-line mode it is logged).

Messages are shown to the user as they are, so they are texts of the plugin in the language of the user interface.

## Packaging the jar

A plugin jar contains:

* the classes of the plugin, its `META-INF/services/org.bitpunk.m3uedit.api.<Kind>` files, its texts and other
  resources,
* the manifest attribute `M3UEdit-Plugin-API` with the version of the API it was built for (`PluginApi.VERSION` of
  that API, currently `1`), see [Maven](#maven) and [Gradle](#gradle); m3uedit only loads jars built for its version,
* the libraries it needs that m3uedit does not provide.

It does **not** contain the plugin API, JavaFX or any other part of m3uedit. Each plugin jar gets a class loader of
its own whose parent is m3uedit and which asks the parent first, so classes of m3uedit always win over copies in the
jar.

**Libraries:** a plugin using libraries puts them into its jar, e.g. with the
[maven-shade-plugin](https://maven.apache.org/plugins/maven-shade-plugin/) or the
[Shadow plugin](https://gradleup.com/shadow/) of Gradle. The libraries m3uedit itself contains (e.g. Jackson,
SLF4J, Logback, Apache Commons, JSch, Hibernate Validator) are not part of the API: they may change or disappear
with any release, and as m3uedit is asked first, a plugin would get the version of m3uedit instead of its own. So a
plugin needing one of these libraries relocates its copy into a package of its own:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <relocations>
                    <relocation>
                        <pattern>org.apache.commons.lang3</pattern>
                        <shadedPattern>com.example.m3uedit.shaded.lang3</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

The shade plugin leaves out dependencies of scope `provided`, so the API and JavaFX stay out of the jar. Two plugin
jars do not see each other's classes.

## Testing

The template contains a unit test (`GroupFilterTest`) running the plugin without m3uedit: it creates the plugin,
passes it a small stand-in for the `PluginContext` (here only providing the texts) and checks filtering, the settings
of an older project-file, the registration for the `ServiceLoader` and that both languages have the same texts.
In Maven, `provided` dependencies are on the test classpath anyway; in Gradle, `testImplementation` extends
`compileOnly` for this.

To try the plugin in m3uedit, [install](#installing) the jar and check:

* `m3uedit --list-plugins` lists the plugin with its jar, or the reason why the jar or the plugin was not loaded;
  *Help → Plugins* shows the same in the user interface.
* `m3uedit --run project.m3uproj -v` runs a project using the plugin without user interface, as a cron job would.
* Started from a terminal, m3uedit logs to the console, including the stack traces of exceptions of plugins.

## Installing

Copy the jar into `~/.m3uedit/extensions/` (created if needed) and start m3uedit again: the plugin is offered like
the built-in ones. Updating means replacing the jar, removing it deleting the jar; both take effect at the next start.
Projects using a plugin that is not installed can still be opened, they keep its settings, see the
[user guide](user-guide.md#plugins-of-other-parties).

## Compatibility

`PluginApi.VERSION` is the version of the API: m3uedit only loads plugin jars declaring the same version in their
manifest. It is raised only by changes that may break plugins built before:

| Raises `PluginApi.VERSION` (incompatible) | Does not raise it (compatible) |
|---|---|
| removing or renaming a type, method, constructor or constant | new types, e.g. a new kind of plugin |
| changing the parameters, the return type or the exceptions of a method | new default methods of the interfaces |
| new abstract methods of the interfaces a plugin implements | new methods and constructors of classes and records |
| changing the meaning of a method, e.g. the thread it is called on or what it may change | new constants |
| changing how settings, type ids or texts are found or stored | fixes of the documentation |

So a plugin built against a release of m3uedit runs in all releases with the same `PluginApi.VERSION`, newer or
older, as long as it does not use what has been added in a release newer than the one it runs in. **Build against
the oldest release you want to support**: the compiler then ensures the plugin only uses what that release has. When
`PluginApi.VERSION` is raised, a plugin has to be built again against the new API (and maybe adapted); the release
notes of m3uedit name such releases.

The build of m3uedit checks these rules: it compares the API with the first release of the current
`PluginApi.VERSION` ([japicmp](https://siom79.github.io/japicmp/), property `plugin-api.baseline` of
`m3uedit-plugin-api/pom.xml`) and fails on incompatible changes. An incompatible change raises `PluginApi.VERSION` and
sets `plugin-api.baseline` to the coming release, see [Versions and releases](development.md#versions-and-releases).
Changes of meaning cannot be found by a tool: they are checked in the review of each change of the API.
