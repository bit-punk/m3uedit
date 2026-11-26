# Configuration

The settings of M3UEditor: the configuration dialog and the files the configuration is stored in.

* [The configuration dialog](#the-configuration-dialog)
* [Configuration files](#configuration-files)
* [A different configuration directory](#a-different-configuration-directory)

## The configuration dialog

The general settings are changed in the dialog *File → Configuration…* (Ctrl+,); they take effect without restart,
except the language:

| Section         | Settings                                                                                         |
|-----------------|--------------------------------------------------------------------------------------------------|
| Appearance      | the theme: light, dark or the one of the system (also followed when it changes while m3uedit is running), applied to all windows at once; the language of the user interface: the one of the system (German or English), English or German; it is changed when m3uedit is started the next time |
| External player | the command (e.g. `vlc`, `mpv` or the path of the program), additional arguments and whether the VLC options of the entries are passed |
| Recently used   | how many recently used projects are remembered |
| Collected data  | the data collected while using m3uedit, with its size, and clearing it: the recently used projects, and the data of the plugins, e.g. the recently used SFTP servers and FritzBoxes and the downloaded EPG sources (downloaded again when needed) |
| Startup         | showing the start screen, or reopening the projects that were open when m3uedit was closed (files that no longer exist are skipped) |

*OK* applies and saves the settings, *Cancel* discards them, also the lists marked to be cleared. The settings of
single plugins (e.g. the expressions of the channel name filter, the EPG sources) are changed in the configuration
dialogs of the plugins, see the [user guide](user-guide.md#the-pipeline).

## Configuration files

Besides the project files, the configuration is stored in the directory `~/.m3uedit`. The files are created with
default values on first use and can be edited by hand:

| File                                   | Content                                                     |
|----------------------------------------|-------------------------------------------------------------|
| `external_player.yaml`                 | command and arguments of the external player (vlc)          |
| `recent_projects.yaml`                 | recently used projects, `maxEntries` sets how many (10)     |
| `settings.yaml`                        | the theme (`LIGHT`, `DARK` or `SYSTEM`), the language (`SYSTEM`, `ENGLISH` or `GERMAN`), what is shown at startup (`START_SCREEN` or `REOPEN_PROJECTS`) and the projects open at the last exit |
| `plugins/channel_name_filter.yaml`     | regular expressions of channel names to filter              |
| `plugins/epg_enricher.yaml`            | EPG sources a new EPG Enricher starts with                  |
| `plugins/fritzbox_recent_connections.yaml` | recently used FritzBoxes and their streams              |
| `plugins/preferred_channel_order.yaml` | profiles with the preferred order of channels               |
| `plugins/reachable_url_filter.yaml`    | connect-timeout used to check whether a stream is reachable |
| `plugins/sftp_recent_servers.yaml`     | recently used SFTP servers (passwords are never stored)     |
| `cache/epg/`                           | downloaded EPG sources                                      |
| `extensions/`                          | jars of plugins of other parties, see the [user guide](user-guide.md#plugins-of-other-parties) |

The files are read again before they are changed, so changes made by hand are kept.

## A different configuration directory

A different directory can be used by setting the system property `m3uedit.config.dir`, e.g.
`java -Dm3uedit.config.dir=/tmp/m3uedit -jar target/m3uedit-<version>.jar`.
