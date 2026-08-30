# PlexonTools 3.6.0

PlexonTools 3.6.0 moves mutable player and tool-instance state to SQLite, makes the bundled YAML formats substantially easier to customize, and preserves the 3.5.2 per-level progression and GUI-isolation fixes.

## SQLite runtime persistence

- `plexontools.db` is generated automatically in `plugins/PlexonTools` on first startup.
- Normal gameplay mutates an in-memory registry only. Repeated changes for the same tool UUID are coalesced and written asynchronously in bounded transactions.
- Immutable profile fingerprints and cumulative-level prefixes are cached instead of being recomputed for every accepted event.
- The database uses prepared statements, foreign keys, indexed owner/tool/world lookups, a busy timeout, integrity checking, and Write-Ahead Logging where the host filesystem supports it.
- Shutdown drains pending writes and checkpoints the WAL before closing.
- `/pt backup` flushes pending records and creates a consistent database copy under `plugins/PlexonTools/backups`.
- SQLite is bundled inside the plugin JAR; server owners do not install a separate database service or JDBC library.

## Automatic `data.yml` migration

When no initialized database exists and a schema-v3 or schema-v4 `data.yml` is present, PlexonTools:

1. Strictly validates every legacy record before opening a new database.
2. Preserves `data.yml.pre-sqlite-<timestamp>.bak` beside the source.
3. Imports all records in one transaction.
4. Verifies identifiers, ownership, world binding, level, progress, target counters, activation state, lifetime, and timestamps.
5. Commits a migration marker only after verification succeeds.

The source YAML remains untouched after migration. A completed migration is never repeated, so stale YAML cannot create duplicate instances. If validation or import fails, startup stops with the source still recoverable instead of partially loading state.

## Administrator-friendly YAML

- Every bundled editable YAML now starts with its accepted structure, value constraints, inheritance rules, and examples.
- `tool-lore.template` in `config.yml` is a freely ordered list. Add, remove, or reorder identity, progress, binding, profile, and decorative rows without changing Java code.
- `{requirement_lines}` expands at its exact template position.
- GENERAL, SPECIFIC, and maximum-level requirement rows each have an independent MiniMessage format under `tool-lore.requirements`.
- A root `lore` list in `tools.yml` overrides the global template for the whole definition; an individual level `lore` overrides both. `lore: []` intentionally removes lore for that scope.
- On every startup, current reference copies of `config.yml`, `tools.yml`, `menus.yml`, `categories.yml`, and `messages.yml` are refreshed under `plugins/PlexonTools/examples`. Live administrator files are never overwritten.
- Older `default_lore_format` settings remain readable for compatibility.

## Progress and GUI behavior retained from 3.5.2

- Requirements are independent at every level boundary. Two consecutive levels that each require 500 Stone blocks require 500 new breaks at each level.
- The completing event cannot carry excess activity into the next level and can advance at most one level.
- Accepted activity can display a configurable progress action bar.
- PlexonTools owns and defers its pagination actions so unrelated inventory plugins cannot replace the intended next page.

## Commands and permissions

| Command | Permission | Purpose |
|---|---|---|
| `/pt backup` | `plexontools.backup` | Flush pending state and create a checkpointed SQLite backup |

`plexontools.admin` includes the backup permission. Existing commands and permissions are unchanged.

## Compatibility and upgrade notes

- Paper `1.21.4` and Java `21` remain required.
- Existing tool items retain their PDC identity and state.
- Existing live administrator YAML remains valid; new settings receive safe in-memory defaults.
- Storage filename, WAL checkpoint, busy-timeout, and startup-integrity settings require a server restart. Other configuration changes can be applied with `/pt reload`.
- `data.yml` is no longer live runtime storage after a successful 3.6 migration.
- The generated database is not encrypted. Protect the plugin directory and its backups with normal filesystem permissions.

See [`docs/PLEXONTOOLS_3_6_0.md`](docs/PLEXONTOOLS_3_6_0.md) for schema, customization, backup, restore, migration, and rollback details.
