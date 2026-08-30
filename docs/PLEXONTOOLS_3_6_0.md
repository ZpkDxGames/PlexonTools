# PlexonTools 3.6.0 — Database and Configuration Guide

PlexonTools 3.6.0 is the first database-backed release. Mutable player and tool-instance state moves from whole-file YAML snapshots to a generated SQLite database, while administrator-authored definitions remain readable and version-controllable YAML.

## Release contract

| Area | Value |
|---|---|
| Plugin version | `3.6.0` |
| Server | Paper `1.21.4` |
| Java | `21` |
| Runtime state | `plugins/PlexonTools/plexontools.db` |
| Database schema | `1` |
| Definition formats | YAML plus `/pt gui` editors |
| Legacy import | `data.yml` schemas `3` and `4` |

The SQLite JDBC implementation is included in the plugin JAR. No external database server or manually installed driver is required.

## Configuration files

| File | Editable purpose | Reload behavior |
|---|---|---|
| `config.yml` | Protection, persistence tuning, progress bar, menu-card defaults, global item lore, effects | Most values use `/pt reload`; storage startup options require restart |
| `tools.yml` | Tool definitions, tracking, requirements, levels, item profiles, lore overrides, abilities | `/pt reload` |
| `menus.yml` | Per-world `/pt` title, size, filler, and pinned card slots | `/pt reload` |
| `categories.yml` | Internal category names, icons, slots, and descriptions | `/pt reload` |
| `messages.yml` | MiniMessage chat, action-bar, editor, backup, and progression feedback | `/pt reload` |

Current documented copies of all five files are written to `plugins/PlexonTools/examples` on startup. These copies are references and may be refreshed by a newer plugin version. PlexonTools never replaces the live files merely because bundled defaults changed.

## Custom physical-tool lore

The default item layout is the ordered `tool-lore.template` list in `config.yml`. Every entry is one lore line. Reorder, add, or remove entries freely; use `""` for a blank line.

```yaml
tool-lore:
  enabled: true
  template:
    - "<gold><bold>{tool}</bold></gold>"
    - "<gray>Level:</gray> <white>{level}/{max_level}</white>"
    - "{requirement_lines}"
    - "{progress_bar}"
    - "<gray>Owner:</gray> <white>{owner_name}</white>"
  requirements:
    general-line: "<gray>{requirement_goal}:</gray> <white>{requirement_current}/{requirement_required}</white>"
    specific-line: "<gray>{requirement_target}:</gray> <white>{requirement_current}/{requirement_required}</white>"
    maximum-line: "<green><bold>FULLY MASTERED</bold></green>"
```

`{requirement_lines}` is expanded exactly where it appears:

- GENERAL: one `general-line`.
- SPECIFIC: one `specific-line` for every configured target.
- Maximum level: one `maximum-line`.

The template supports identity, binding, profile, requirement, counter, percentage, and progress-bar placeholders documented directly above the setting in the generated `config.yml`. Both `{placeholder}` and `<placeholder>` forms remain accepted.

### Per-tool and per-level overrides

A root `lore` list in one `tools.yml` definition replaces the global template for that tool. A level's `lore` list replaces both the root and global value for that level.

```yaml
tools:
  builder_wand:
    lore:
      - "<aqua>{tool}</aqua>"
      - "{requirement_lines}"
    levels:
      10:
        lore:
          - "<light_purple>Master Builder</light_purple>"
```

An explicit `lore: []` means no lore at that scope. Omitting `lore` means inherit. Set `tool-lore.enabled: false` to remove the global template from definitions that do not provide an override.

## Independent level requirements

A requirement belongs only to its current level. Completing a level resets aggregate progress and all SPECIFIC target counters before the next level begins. The completing event's overflow is discarded and one event advances at most one level.

For example, these levels require 1,000 total Stone breaks, performed as two separate groups of 500:

```yaml
levels:
  1:
    requirement_mode: SPECIFIC
    requirements:
      STONE: 500
  2:
    requirement_mode: SPECIFIC
    requirements:
      STONE: 500
```

Set `effects.progress-action-bar: true` and customize `messages.progress-update` to show live progress after each accepted event.

## Database design

The runtime database contains four schema areas:

| Table | Purpose |
|---|---|
| `schema_metadata` | Schema and legacy-migration metadata |
| `players` | Owner UUID, cached name, and timestamps |
| `tool_instances` | Unique tool identity, ownership, binding, level, progress, activation, lifetime, and timestamps |
| `target_progress` | Normalized SPECIFIC counters keyed by instance and target |

Owner, tool, owner/tool/world, and active-owner/world access paths are indexed. Foreign keys prevent target counters or tool instances from referencing missing parent records.

Gameplay listeners update the concurrent in-memory registry and enqueue only the changed UUID. A scheduled asynchronous worker coalesces repeated changes, writes at most `storage.write-batch-size` records per transaction, and bounds its queue with `storage.max-pending-writes`. If that bound is reached, it safely collapses the pending UUID set into a full current snapshot instead of dropping state.

This design keeps YAML parsing and database access out of normal block, combat, farming, fishing, damage, and placement event handlers. It does not move Bukkit inventory, item, entity, or world API work off the server thread.

### Storage settings

| Key | Default | Constraint / effect |
|---|---:|---|
| `storage.database-file` | `plexontools.db` | Simple `.db` filename inside the plugin directory; restart required |
| `storage.flush-interval-ticks` | `20` | `1`–`72000`; async drain cadence |
| `storage.write-batch-size` | `256` | `1`–`4096`; records per transaction |
| `storage.max-pending-writes` | `8192` | At least the batch size, up to `100000` |
| `storage.busy-timeout-ms` | `5000` | `250`–`60000`; restart required |
| `storage.wal-autocheckpoint-pages` | `1000` | `1`–`100000`; restart required |
| `storage.integrity-check-on-startup` | `true` | Runs SQLite `integrity_check`; restart required |

WAL is requested at startup. If the host filesystem cannot use it, PlexonTools logs the actual journal mode and retains asynchronous transactions. SQLite provides consistency, not encryption; protect live and backup files with filesystem permissions.

## Automatic legacy migration

When `plexontools.db` is absent or empty and `data.yml` exists, startup performs this sequence:

1. Parse schema `3` or `4` and validate every field and record.
2. Stop before database creation if any record is invalid.
3. Copy the source to `data.yml.pre-sqlite-<UTC timestamp>.bak`.
4. Import all players, instances, and target counters in one transaction.
5. Read the imported state back and compare every persistent value.
6. Store the source SHA-256, backup filename, record count, and completion time as migration metadata.
7. Commit only when verification succeeds.

The original `data.yml` remains beside the database but becomes historical. It is not updated and must not be edited as live 3.6 state. A completed marker makes later startups idempotent. If a non-empty database and an unmarked YAML both exist, PlexonTools warns and prefers the database rather than guessing which state should win.

## Backup and restore

Run `/pt backup` with `plexontools.backup`. PlexonTools drains queued changes, checkpoints the WAL, and creates `plugins/PlexonTools/backups/plexontools-<UTC timestamp>.db` without stopping the live database.

To restore a backup:

1. Stop the Paper server completely.
2. Preserve the current database and any matching `-wal` and `-shm` files.
3. Copy the chosen backup to the filename configured by `storage.database-file`.
4. Remove stale `-wal` and `-shm` sidecars belonging to the replaced database while the server is stopped.
5. Start the server and confirm the schema, integrity check, instance count, and a representative player's tools in the log/game.

Never replace or copy a live SQLite file manually while Paper is running. Prefer `/pt backup` for a consistent online copy.

## Rollback to 3.5.2

1. Stop Paper and back up the complete `plugins/PlexonTools` directory.
2. Preserve `plexontools.db`, its sidecars, and all 3.6 backups.
3. Restore the original pre-migration `data.yml` or its `data.yml.pre-sqlite-...bak` copy.
4. Replace the plugin JAR with `PlexonTools-3.5.2.jar` and start Paper.

PlexonTools 3.5.2 cannot read the SQLite database. State changed after the 3.6 migration is therefore not present in the historical YAML and will be lost from the 3.5.2 view. Treat rollback as an emergency recovery path, not a bidirectional conversion.

## Verification checklist

- Confirm startup reports the configured runtime database and no integrity or migration error.
- Confirm `plexontools.db` exists after first startup.
- Activate, progress, deactivate, relog, and reactivate a representative tool.
- Test a repeated requirement across two levels and confirm the second begins at zero.
- Run `/pt backup` and verify a timestamped `.db` appears under `backups`.
- Restart Paper and confirm UUID, owner, world, level, aggregate progress, target counters, and activation state remain unchanged.
- Keep a complete plugin-directory backup before every upgrade.
