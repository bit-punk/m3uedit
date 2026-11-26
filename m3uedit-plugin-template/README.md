# A plugin for m3uedit

A template for plugins of [m3uedit](https://github.com/bit-punk/m3uedit): a filter removing the entries of chosen
groups (`group-title`), or keeping only them. It shows the parts of a plugin:

| File | Content |
|---|---|
| `src/main/java/com/example/m3uedit/GroupFilter.java` | the plugin: its identity (`@PluginType`), filtering, the configuration dialog |
| `src/main/java/com/example/m3uedit/GroupFilterSettings.java` | its settings, stored in the project-files |
| `src/main/resources/META-INF/services/org.bitpunk.m3uedit.api.Filter` | registers the plugin |
| `src/main/resources/com/example/m3uedit/messages*.properties` | its texts in English and German |
| `src/test/java/com/example/m3uedit/GroupFilterTest.java` | a test running the plugin without m3uedit |
| `pom.xml` | the build against the published plugin API, with the manifest attribute `M3UEdit-Plugin-API` |

## Making it your own

1. Change the `groupId`, `artifactId` and `name` in `pom.xml`.
2. Rename the package `com.example.m3uedit` (also in `META-INF/services/…` and in `texts` of `@PluginType`).
3. Choose a type id of your own in `@PluginType(id = …)`: it is stored in the project-files of your users and must
   never change afterwards.
4. Set `m3uedit.version` in `pom.xml` to the oldest release of m3uedit your plugin supports.

## Building and installing

```bash
./mvnw package      # or: mvn package
```

builds `target/m3uedit-group-filter-1.0.0-SNAPSHOT.jar`. Copy it into `~/.m3uedit/extensions/` and start m3uedit
again; `m3uedit --list-plugins` shows whether it has been loaded.

The guide for plugin authors, [Writing plugins](https://github.com/bit-punk/m3uedit/blob/develop/docs/plugins.md),
describes the API, the settings, texts, dialogs, packaging libraries and the compatibility of the API.
