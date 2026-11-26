# User guide

How to work with M3UEditor: projects, the pipeline computing the playlist, editing the playlist, the diff and the
programme guide.

* [Projects](#projects)
* [The pipeline](#the-pipeline)
  * [Inputs](#inputs)
  * [Filters](#filters)
  * [Sorters](#sorters)
  * [Enrichers and the EPG Enricher](#enrichers-and-the-epg-enricher)
  * [Outputs](#outputs)
* [The playlist](#the-playlist)
* [The diff](#the-diff)
* [The programme guide](#the-programme-guide)
* [Plugins of other parties](#plugins-of-other-parties)
* [Keyboard shortcuts](#keyboard-shortcuts)

The settings of M3UEditor are described in [Configuration](configuration.md), running projects without user
interface in [Command-line mode](command-line.md).

## Projects

A project (`*.m3uproj`) contains everything needed to create a playlist: its inputs, filters, sorters, enrichers and
outputs with their settings, and the changes made to the playlist by hand. The entries themselves are not stored:
they are read from the inputs again when the project is opened, so the playlist is re-created with the current
channels of the inputs.

* Projects are created, opened and saved in the *File* menu, on the start screen and with the toolbar; several
  projects can be open at once, each in a tab.
* A tab shows an asterisk after the name of a project with unsaved changes (e.g. `tv *`); closing it or m3uedit asks
  before the changes are lost.
* The recently used projects are listed on the start screen, in *File → Open Recent Projects* and in the list of the
  *Open* button of the toolbar. A project-file that no longer exists is removed from the list.
* The projects open when m3uedit is closed can be reopened at the next start, see [Configuration](configuration.md).

## The pipeline

The left side of a project shows the pipeline computing the playlist. Each step works on the result of the previous
one; a change of a step re-computes this step and all following ones in the background.

```
┌──────────┐ ┌──────────┐       ┌──────────┐
│ Input #1 │ │ Input #2 │  ...  │ Input #n │   files, URLs, FritzBox, iptv-org, SFTP
└────┬─────┘ └────┬─────┘       └────┬─────┘
     └────────────┼──────────────────┘
           ┌──────▼──────┐
           │  Filter #1  │                     enabled filters, from top to bottom
           │     ...     │
           │  Filter #n  │
           └──────┬──────┘
           ┌──────▼──────┐
           │   Sorter    │                     at most one sorter is enabled
           └──────┬──────┘
           ┌──────▼──────┐
           │ Enricher #1 │                     e.g. the EPG Enricher: sets tvg-ids and
           │     ...     │                     creates the programme guide
           │ Enricher #n │
           └──────┬──────┘
         ┌────────▼─────────┐
         │  Manual Changes  │                  entries edited, added, removed or moved by
         └────────┬─────────┘                  hand, applied again after each re-computation
            ┌─────▼────┐
            │ Playlist │                       shown in the playlist table and the programme guide
            └─────┬────┘
     ┌────────────┼──────────────────┐
┌────▼──────┐ ┌───▼───────┐       ┌──▼────────┐
│ Output #1 │ │ Output #2 │  ...  │ Output #n │  the playlist and the files of the enrichers
└───────────┘ └───────────┘       └───────────┘
```

Each stage of the pipeline is a box with a number:

* Filters, sorters and enrichers are enabled with their checkbox (or all at once with the checkbox of the stage) and
  reordered with the arrows; plugins with settings have a button to configure them.
* Inputs and outputs are added with the *+* button of their stage and removed with the *×* of their row (or the
  *Del* key).
* Each row shows what it did as a badge: the number of entries read, removed, moved or changed. An input that could
  not be read is marked red, its tooltip names the error; the other inputs are used anyway.
* Between the stages, the number of entries passed on is shown, and above the playlist a summary: loaded &rarr;
  removed by filters &rarr; in the playlist, with a bar showing which filter removed how many entries.
* Stages can be collapsed; a collapsed stage shows its status instead of its rows.
* While no entries have been read, the following stages are greyed out (they can still be configured).
* Selecting a row shows its effect in the [diff](#the-diff).
* *Project → Reload Inputs* (F5) or the toolbar reads all inputs again, e.g. to get the current channels.

### Inputs

Inputs read the entries of the playlist. The entries of all inputs are merged in the order of the inputs.

| Input | Reads |
|---|---|
| File Input | a playlist from a local file |
| Url Input | playlists from one or more URLs |
| FritzBox Input | the SD and/or HD streams of a FritzBox with TV tuner, recently used FritzBoxes are offered |
| IPTV-org Input | playlists of [iptv-org](https://github.com/iptv-org/iptv) by country, category, language, region or single channels |
| SFTP Input | a playlist from an SFTP server, with a browser for its directories; recently used servers are offered |

The EPG URLs of the inputs (`x-tvg-url` / `url-tvg` in their playlist headers) are kept, unless the EPG Enricher
creates a programme guide of its own.

SFTP passwords are never stored: m3uedit asks for them when connecting and keeps them until it is closed. Unknown
host keys have to be confirmed once.

### Filters

Filters remove entries; the [diff](#the-diff) shows which filter removed an entry and why.

| Filter | Removes | Reason shown, e.g. |
|---|---|---|
| ChannelName Filter | channels whose name matches one of a list of regular expressions | `matches .*Shop.*` |
| SD Channel Filter | SD channels whose name also exists with "HD" | `"ZDF HD" exists` |
| Unique Name Filter | entries with the name of an earlier entry | `same name as "ZDF" (#3)` |
| Unique URI Filter | entries with the stream URL of an earlier entry | `same URL as "Das Erste HD" (#1)` |
| Reachable URL Filter | entries whose stream cannot be played (http: GET incl. HLS playlists, rtsp: DESCRIBE), within a configurable time | `stream not playable: …` |

### Sorters

Sorters sort the entries completely, so only one of them can be enabled.

| Sorter | Sorts |
|---|---|
| ChannelName Sorter | alphabetically by name |
| Preferred Channel Order Sorter | by a preferred order of channels; several profiles can be kept, a profile can be created from an existing playlist |

### Enrichers and the EPG Enricher

Enrichers add information to the entries and may create additional files written next to the playlist.

The **EPG Enricher** adds a programme guide: it assigns the entries to the channels of XMLTV sources (e.g.
[epgshare01](https://epgshare01.online)) by setting their `tvg-id`, matched in this order by

1. the `tvg-id` of the entry,
2. URL rules: regular expressions on the stream URL, each with the channel id it stands for (`service=514 => ZDF.de`),
3. the (tvg-)name of the entry,
4. the first words of the name (a guess, e.g. "WDR Köln" for "WDR").

It can write a guide containing only the channels of the playlist next to it (`tv.m3u` &rarr; `tv.xml.gz`),
referenced by the playlist header if the output knows its URL. The sources are downloaded to `~/.m3uedit/cache/epg`
and downloaded again after a configurable time; the size of the cache is shown and it can be cleared in the
[configuration](configuration.md).

### Outputs

Outputs write the playlist and the files of the enrichers (e.g. the programme guide).

| Output | Writes to |
|---|---|
| File Output | a local file |
| SFTP Output | a file on an SFTP server; before an existing file is overwritten, m3uedit asks (unless switched off for this target) |

*Write playlist* below the pipeline (or *Project → Write Playlist*, or the toolbar) writes to all outputs; it needs an
output and entries.

## The playlist

The right side of a project shows the playlist as computed by the pipeline, with the logos of the channels.

* **Search:** the field above the table (Ctrl+F) shows only the entries whose name, group, `tvg-id` or URL contain
  the text; Esc clears it. The position shown is always the position in the whole playlist.
* **Play:** the ▶ of a row (or the toolbar) plays the entry in the external player (VLC by default, see
  [Configuration](configuration.md)).
* **Edit:** a double click (or Enter, or *Edit → Edit Entry…*) opens the entry in a dialog: name, stream URL, the
  `tvg-*` attributes with a preview of the logo, group, duration, radio, position and the VLC options.
* **Add:** *Add new Entry* below the table (or the toolbar) adds an entry at the end of the playlist.
* **Remove:** *Del* (or *Edit → Delete*) removes the selected entries after asking.
* **Move:** entries are moved by drag and drop, or by changing their position in the edit dialog.
* **Copy and paste:** *Edit → Cut / Copy / Paste* (Ctrl+X / C / V) work with the selected entries as M3U text, so
  entries can be copied between projects and to or from a text editor. Pasted entries are inserted behind the last
  selected entry (or at the end) and selected afterwards.
* **Undo and redo:** *Edit → Undo / Redo* (Ctrl+Z, Ctrl+Y or Ctrl+Shift+Z) undo every change made to the playlist by
  hand, from the opening of the project on; a change of several entries is one step.

While the cursor is in a text field (e.g. the search), the functions of the *Edit* menu work on its text instead of
the entries.

**Manual changes** are kept as a list of changes, not as a copy of the playlist: they are applied again each time
the pipeline is re-computed, e.g. after the inputs have been read again, so an entry edited by hand keeps its changes
as long as the input delivers it. *Reset manual Changes* below the table (or *Edit → Reset Manual Changes*) discards
them after asking. A project can even consist of manual entries only, without any input.

## The diff

The tab *Diff* next to the playlist compares the entries before and after a step of the pipeline, side by side:

* Select a row of the pipeline to see its effect, e.g. the entries a filter removed (with the reason), the entries a
  sorter moved, the entries an enricher changed (e.g. `tvg-id: ZDF.de`, with the way it was matched). Without a
  selection (or with *Show whole pipeline*), the entries loaded from the inputs are compared to the playlist.
* Removed entries are marked orange, moved ones blue, added ones green, changed ones yellow; the counts are shown
  above the lists.
* *Changes only* hides the unchanged entries (they can be unfolded), *All rows* shows them.
* The search field filters both lists by channel name.

## The programme guide

With an enabled EPG Enricher, the *EPG* button below the playlist (or *Project → Programme Guide*, or the toolbar)
opens a programme guide like on a TV: a row per channel of the playlist (with its number and logo), the programmes as
blocks along a time axis.

* A line marks the current time, past programmes are dimmed, running ones show their progress.
* *Now*, the day and the part of the day (morning, afternoon, evening, night) jump to a time; the mouse wheel and the
  scroll bars scroll the programmes.
* A click shows the details of a programme (title, time, description, image); the arrow keys move the selection.
* Double click or Enter plays the channel in the external player.
* The search field finds programmes by title or description.
* The guide follows the changes of the playlist and of the pipeline while it is open.

Only one programme guide window is open per project.

## Plugins of other parties

Inputs, filters, sorters, enrichers and outputs are plugins. Besides the built-in ones, plugins of other parties can
be installed:

1. Copy the jar of the plugin into the directory `extensions` of the configuration directory, `~/.m3uedit/extensions/`
   (created if needed).
2. Start m3uedit again: the plugin is offered like the built-in ones, e.g. in the add-menu of the inputs, or as an
   additional filter in the pipeline. Projects using it store its settings like those of the built-in plugins.

Updating a plugin means replacing its jar, removing it deleting its jar; both take effect at the next start.
*Help → Plugins* lists all plugins with their source (built-in, or the jar), the directory to install them in, and the
jars that could not be loaded with the reason, e.g. a jar built for another version of m3uedit. In the command-line
mode, `m3uedit --list-plugins` prints the same.

**A plugin runs with your rights, like m3uedit itself: only install plugins you trust.** m3uedit never downloads
plugins, and opening a project-file never loads a plugin.

## Keyboard shortcuts

On macOS, Cmd is used instead of Ctrl for the functions of the *Edit* menu, the configuration and the search; the
functions of the *File* menu use Ctrl.

| Keys | Function |
|---|---|
| Ctrl+N | New project |
| Ctrl+O | Open project |
| Ctrl+S | Save project |
| Ctrl+Shift+S | Save project as |
| Ctrl+W | Close project |
| Ctrl+, | Configuration |
| Ctrl+Z | Undo |
| Ctrl+Y, Ctrl+Shift+Z | Redo |
| Ctrl+X / Ctrl+C / Ctrl+V | Cut / copy / paste entries (or text in a text field) |
| Del | Delete the selected entries (or input, output) |
| Ctrl+A | Select all entries shown |
| Enter | Edit the selected entry |
| Ctrl+F | Search the playlist |
| Esc | Clear the search |
| F5 | Reload the inputs |
