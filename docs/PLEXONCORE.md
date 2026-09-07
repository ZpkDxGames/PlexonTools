# PlexonTools and PlexonCore

PlexonTools 4.1.0 optionally integrates with PlexonCore 1.0.0 while retaining complete standalone operation.

## Dependency model

PlexonCore is a compile-only/provided dependency. The final PlexonTools JAR must not contain `com/zpkdxgames/plexoncore/` classes. CI provisions the exact Core 1.0.0 release JAR and verifies SHA-256:

```text
4abce6de93293e21b31cb874734430d5bdc77de17a4c3b98fd6a9006e1f13018
```

`plugin.yml` uses `softdepend: [PlexonCore]`.

## Modes

```text
Core present + API >=1.0 <2.0 + module registration succeeds -> CORE
Core absent/disabled/incompatible/unavailable                -> STANDALONE
```

Standalone mode still provides `/pt`, progression, levels, SQLite, abilities, the public PlexonTools API, and both public progression events.

## Module

PlexonTools registers:

```text
id: tools
display: PlexonTools
supported Core API: >=1.0 <2.0
```

Capabilities:

```text
tool-engine
progressive-tools
tool-api
tool-progress-event
tool-level-up-event
sqlite-persistence
custom-item-metadata
world-bound-tools
tool-categories
tool-abilities
```

The bridge registers STARTING before PlexonTools initialization, marks READY after definitions, SQLite, services, commands/listeners, public API, events, and schedulers are initialized, can report DEGRADED/FAILED for failures, and unregisters its own `tools` module on disable.

Core-dependent bridge code is reflectively loaded only when the PlexonCore plugin is enabled. This prevents `NoClassDefFoundError` in standalone mode.

## Diagnostics

`/pt diagnostics` reports the plugin/Paper/Java versions, mode, Core plugin/API versions, module state/detail, SQLite journal mode, tool/category/world-menu and instance counts, pending persistence work, natural-block mode, public API registration, and public event availability. It does not expose secrets or absolute database paths.
