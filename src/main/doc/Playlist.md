# Playlist

The playlist of a project is computed by a pipeline of plugins (see `DataModel`). Each step works on the result of
the previous one; a change of a step re-computes this step and all following ones in the background.

```
┌──────────┐ ┌──────────┐       ┌──────────┐
│ Input #1 │ │ Input #2 │  ...  │ Input #n │   files, URLs, FritzBox, iptv-org, SFTP
└────┬─────┘ └────┬─────┘       └────┬─────┘
     └────────────┼──────────────────┘
           ┌──────▼──────┐
           │ Raw Entries │
           └──────┬──────┘
           ┌──────▼──────┐
           │  Filter #1  │                     enabled filters, in their order
           │     ...     │
           │  Filter #n  │
           └──────┬──────┘
         ┌────────▼─────────┐
         │ Filtered Entries │
         └────────┬─────────┘
           ┌──────▼──────┐
           │   Sorter    │                     at most one sorter is enabled
           └──────┬──────┘
          ┌───────▼────────┐
          │ Sorted Entries │
          └───────┬────────┘
           ┌──────▼──────┐
           │ Enricher #1 │                     e.g. the EPG Enricher: sets tvg-ids and
           │     ...     │                     creates the programme guide (attachment)
           │ Enricher #n │
           └──────┬──────┘
         ┌────────▼─────────┐
         │ Enriched Entries │
         └────────┬─────────┘
         ┌────────▼─────────┐
         │  Manual Changes  │                  entries edited, added, removed or moved by
         └────────┬─────────┘                  the user, applied again after each re-computation
            ┌─────▼────┐
            │ Playlist │                       shown in the playlist table and the programme guide
            └─────┬────┘
     ┌────────────┼──────────────────┐
┌────▼──────┐ ┌───▼───────┐       ┌──▼────────┐
│ Output #1 │ │ Output #2 │  ...  │ Output #n │  the playlist and the attachments of the enrichers
└───────────┘ └───────────┘       └───────────┘
```

* **Inputs** read entries (and the EPG urls of their playlist headers); an input that cannot be read is skipped and
  reported.
* **Filters** remove entries; the diff shows which filter removed an entry and why.
* **Sorters** sort the entries completely, so only one of them can be enabled.
* **Enrichers** add information to the entries, e.g. the `tvg-id` of the channel in a programme guide, and may
  create additional files written next to the playlist.
* **Manual changes** of the user are kept as a list of changes, so they survive a re-computation of the pipeline;
  they can be reset at any time.
* **Outputs** write the playlist, e.g. to a local file or an SFTP server.
