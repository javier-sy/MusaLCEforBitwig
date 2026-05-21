# MusaLCE for Bitwig

**Bitwig Studio Controller Extension for the Musa Live Coding Environment.**

A Bitwig 5+ controller extension (Java, Bitwig Extension API 18) that bridges Bitwig Studio with the [musalce-server](https://github.com/javier-sy/musalce-server) Ruby gem. The score code you write in [Visual Studio Code](https://github.com/javier-sy/MusaLCEClientForVSCode) is evaluated by `musalce-server`, which uses this extension to drive Bitwig (MIDI clock sync, MIDI note output, optional Stream Deck surface relay via **Pulso Bridge** — [yeste.studio](https://yeste.studio)'s upcoming Stream Deck plugin for the MusaLCE Surface protocol; public release pending).

## How it fits in the suite

```
[VSCode + MusaLCEClientForVSCode] ──TCP 1327─▶ [musalce-server] ──OSC/UDP──▶ [MusaLCEforBitwig] ──▶ [Bitwig Studio]
                                                                                       ▲
                                                                                       │ optional
                                                                       [Pulso Bridge] ─┘
                                                                              ▲
                                                                              │
                                                                       [Stream Deck plugin]
```

This component is the DAW-side endpoint. It is part of the **suite workflow** of MusaLCE — for the standalone REPL workflow (no server, no extension), see the [MusaLCEClientForVSCode README](https://github.com/javier-sy/MusaLCEClientForVSCode#readme).

For the full architecture of the suite (component responsibilities, REPL DSL surface, `surface[:event]` examples, Stop/Play semantics, and the OSC handler + surface contracts in detail), see the canonical reference: [musalce-server/docs/architecture.md](https://github.com/javier-sy/musalce-server/blob/master/docs/architecture.md).

## Requirements

- Bitwig Studio 5 or higher
- macOS, Linux or Windows
- [musalce-server](https://github.com/javier-sy/musalce-server) installed (either as a system gem or invoked from a project's `Gemfile`). The extension can launch it for you (see *Server lifecycle* below) or you can run it yourself with `musalce-server bitwig`.

## Install

```bash
cd MusaLCEforBitwig
mvn package
```

Build produces `target/MusaLCEforBitwig.bwextension`. Copy it to your Bitwig Extensions directory:

| OS | Path |
|---|---|
| macOS | `~/Documents/Bitwig Studio/Extensions/` |
| Linux | `~/Bitwig Studio/Extensions/` |
| Windows | `%USERPROFILE%\Documents\Bitwig Studio\Extensions\` |

Quit and relaunch Bitwig (or toggle the controller in *Settings → Controllers*) to pick up the new build.

## Enable in Bitwig

1. Open Bitwig **Settings → Controllers**.
2. Click **+ Add Controller**.
3. Pick *yeste.studio → MusaLCE*.
4. Assign **MIDI input** (used as the clock source when *Clock Sender* is enabled — see preferences below).

The extension exposes 1 MIDI input and 0 MIDI outputs at the Bitwig level. MIDI notes from the server reach Bitwig tracks via the standard route MIDI Remote Script port.

## Preferences

The extension exposes three categories of preferences under *Settings → Controllers → MusaLCE → Settings*:

### Configuration

| Setting | Type | Default | Purpose |
|---|---|---|---|
| Controller Name | string | auto | Friendly name shown in Bitwig. Each instance must have a unique name. Renaming triggers a delayed restart. |
| Osc Host | boolean | off | If on, this instance acts as the OSC endpoint that talks to `musalce-server`. Required for the server-driven workflow. |
| Clock Sender | boolean | off | If on, this instance forwards its MIDI input to the server as the master clock. |

### Server

| Setting | Type | Default | Purpose |
|---|---|---|---|
| Start Server | boolean | off | If on (and *Osc Host* is on), the extension launches `musalce-server` as a child process when it initialises. |
| Reload | signal (button) | — | Restarts the child `musalce-server` process. Useful after editing the score's setup files (anything not hot-reloadable from the REPL). Only acts if both *Osc Host* and *Start Server* are on. |

### Pulso Bridge

These configure the OSC link between this extension and the **Pulso Bridge** controller (the bridge between MusaLCE Surface controls and the Stream Deck plugin). Setting them here is only useful if you also run Pulso; otherwise they are inert. The matching settings must be set on the Pulso side (its *MusaLCE* category) so both ends agree.

> **Note:** Pulso is [yeste.studio](https://yeste.studio)'s commercial Stream Deck plugin for the MusaLCE Surface protocol, currently in private development. Public release pending — the integration described here is implemented in this extension today and is exercised by users of the (pre-release) Pulso.

| Setting | Default | Purpose |
|---|---|---|
| Pulso Bridge send host | `127.0.0.1` | Host where the Pulso Bridge is listening. |
| Pulso Bridge send port (out) | `21012` | Port to send Surface messages to Pulso Bridge. |
| Pulso Bridge listen port (in) | `20002` | Port this extension listens on for inbound Surface messages from Pulso Bridge. |

### Per-project (Document State)

Settings under *Studio I/O Panel → MusaLCE* are stored per Bitwig project (not globally):

- **Port → Name**: friendly name of the virtual MIDI port the extension exposes for this project.
- **Channels → Channel 0..N**: MIDI channel-to-track mapping.

## OSC ports

| Direction | Port | Configurable? |
|---|---|---|
| musalce-server → extension | 10001 (UDP) | **No — hardcoded** in `musalce-server`. |
| extension → musalce-server | 11011 (UDP) | **No — hardcoded** in `musalce-server`. |
| Pulso Bridge → extension | 20002 (UDP, default) | Yes, in *Pulso Bridge* prefs. |
| extension → Pulso Bridge | 21012 (UDP, default) | Yes, in *Pulso Bridge* prefs. |

The musalce-server channel is hardcoded on the gem side; this extension cannot be made to talk on different ports without a matching change to `musalce-server/lib/daw.rb`.

## Pulso Bridge integration

The class `MusaLCESurfaceRelay` (`src/main/java/.../MusaLCESurfaceRelay.java`) is a bidirectional verbatim relay between `musalce-server` and **Pulso Bridge** for the **Surface protocol** (`/musalce/surface/*`). It does not validate or aggregate — it just forwards. The protocol (inventory, state propagation, trigger dispatch) is implemented in this codebase and documented in the source file's javadoc; a high-level overview lives in [musalce-server/docs/architecture.md](https://github.com/javier-sy/musalce-server/blob/master/docs/architecture.md). The canonical wire-protocol spec will be linked here once Pulso publishes.

In short: a Stream Deck button mapped to a MusaLCE Surface event reaches the score's `on :event do |payload| … end` handler through this extension.

## Server lifecycle

When *Osc Host* and *Start Server* are both enabled, the extension spawns `musalce-server` as a child process on init and tears it down on exit. The *Reload* signal restarts the child cleanly (~1 second delay between stop and start).

If you prefer to run `musalce-server bitwig` yourself in a terminal (useful for seeing its stdout), leave *Start Server* off and start the server manually before enabling the controller in Bitwig.

## Troubleshooting

Logs are written to:

- `$TMPDIR/musalceserver.log` (macOS, Linux)
- `$TMPDIR/bitwig-<user>/musalceserver.log` for some Linux setups

```bash
tail -f $TMPDIR/musalceserver.log
```

The controller name must be unique across enabled instances. If the rename popup says "*name already in use*", pick another or disable the conflicting instance first.

For Java-level debugging:
```bash
launchctl setenv BITWIG_DEBUG_PORT 5005
```

## Versioning

The displayed version comes from `version.properties`, populated at build time by `maven-antrun-plugin` from `git describe --tags --always --dirty`. A leading `v` in the tag is stripped (so `v0.2` shows as `0.2`). When built outside a git checkout, the version falls back to `0-dev`.

## More from the MusaDSL ecosystem

- [MusaDSL](https://musadsl.yeste.studio) — Ruby framework for algorithmic sound and musical composition.
- [MusaLCE](https://musalce.yeste.studio) — live coding environment for Ableton Live and Bitwig Studio.
- [Nota](https://nota.yeste.studio) — Claude Code plugin for AI-assisted MusaDSL composition.
- **Pulso** — Stream Deck plugin for MusaLCE Surface controls *(upcoming)*.
- **VST3 plugins** — collection of audio plugins for mixing and mastering *(upcoming)*.

For the full map — descriptions, how they relate, which one to pick for what — see [musa-ecosystem.yeste.studio](https://musa-ecosystem.yeste.studio).

## Acknowledgements

Thanks to Jürgen Moßgraber ([YouTube Channel](https://www.youtube.com/channel/UCMgtq3iKqYamt9C-xbxwjTA)) for his Bitwig Controller API Tutorial. It has been a great source for understanding the Bitwig API and starting this project.

## License

[MusaLCEforBitwig](https://github.com/javier-sy/MusaLCEforBitwig) Copyright (c) 2021-2026 [Javier Sánchez Yeste](https://yeste.studio), licensed under GPL 3.0 License.
