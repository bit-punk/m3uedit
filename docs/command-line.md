# Command-line mode

A project created with the user interface can be run without it, e.g. by a cron job that keeps a playlist up to
date.

* [Running a project](#running-a-project)
* [Options](#options)
* [When the playlist is written](#when-the-playlist-is-written)
* [Exit codes](#exit-codes)
* [SFTP passwords](#sftp-passwords)
* [The command on each platform](#the-command-on-each-platform)
* [Example: a cron job](#example-a-cron-job)

## Running a project

The inputs are read, the filters, the sorter and the enrichers are applied and the playlist is written to the
outputs of the project:

```bash
m3uedit --run ~/playlists/tv.m3uproj
```

Without options, the user interface is started as before.

## Options

| Option                      | Meaning                                                                         |
|-----------------------------|---------------------------------------------------------------------------------|
| `-r`, `--run <project-file>` | the project to run; can be given several times, the projects run one after another |
| `-n`, `--dry-run`           | run the pipeline, but do not write to the outputs                               |
| `--ignore-input-errors`     | write the playlist although some inputs could not be read                       |
| `-q`, `--quiet`             | only print errors                                                               |
| `-v`, `--verbose`           | print details of the run                                                        |
| `-h`, `--help`              | print the help                                                                  |
| `--list-plugins`            | print the plugins: the built-in ones, those installed in `~/.m3uedit/extensions`, and the jars that could not be loaded |
| `--version`                 | print the version                                                               |

Plugins of other parties installed in `~/.m3uedit/extensions` are used as in the user interface; a jar that cannot
be loaded is reported on stderr, and a project using one of its plugins cannot be run.

The output of the command-line mode is always in English, whatever language is set for the user interface.

## When the playlist is written

**A working playlist is not replaced by an incomplete one:** it is not written if an input could not be read (e.g.
the FritzBox is offline), if the inputs did not deliver any entries or if the playlist is empty after filtering.
`--ignore-input-errors` writes the entries of the inputs that could be read, but never an empty playlist. A project
without inputs, whose playlist has been created by hand, is written as it is.

## Exit codes

`0` all projects have been run and written, `1` a project could not be run or written, `2` wrong options or a
project-file that could not be loaded. The result of each project is printed to stdout, errors to stderr.

## SFTP passwords

The passwords of SFTP inputs and outputs are not stored in the project, they are taken from environment variables:

| Variable                               | Password of                                                        |
|----------------------------------------|--------------------------------------------------------------------|
| `M3UEDIT_SFTP_PASSWORD_<USER>_<HOST>` | the user `<user>` on the server `<host>`, e.g. `M3UEDIT_SFTP_PASSWORD_ROOT_192_168_178_22` for `root@192.168.178.22` |
| `M3UEDIT_SFTP_PASSWORD`                | all other servers, e.g. if there is only one                       |

User and host are written in upper case, all characters except letters and digits are replaced by `_`. If a
password is missing, the error names the variable expected. Unknown host keys are rejected, so connect to a server
once using the user interface to accept its key. Existing files on the server are overwritten without asking.

## The command on each platform

The command is `m3uedit/bin/m3uedit` in the Linux download (or `java -jar m3uedit-<version>-linux.jar` with a JDK
25+) and `m3uedit.app/Contents/MacOS/m3uedit` on macOS. The launcher of the Windows download has no console, so the
output of a run is not shown there.

## Example: a cron job

Updating the playlist every night at 4:00 (`crontab -e`):

```
0 4 * * * M3UEDIT_SFTP_PASSWORD_ROOT_192_168_178_22='…' /opt/m3uedit/bin/m3uedit --quiet --run /home/me/playlists/tv.m3uproj
```

With `--quiet`, cron only sends a mail if something went wrong.
