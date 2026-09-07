# PlexonTools 4.0.0 -> 4.1.0 Migration

## Source recovery provenance

The repository visible before this migration ended at the 3.6.1 lineage while the production server was running PlexonTools 4.0.0. The migration therefore did not start from stale 3.x source.

The production `PlexonTools-4.0.0.jar` supplied for recovery has SHA-256:

```text
38cbd17346914e3c4c62a5eafbe30cc705e5ec76ad77135efb7ed246a110ce24
```

The maintained 4.0.0 baseline was reconstructed against the authored 3.6.1 tree, with production behavior restored cleanly rather than publishing raw decompiler output. The recovered baseline was committed to `release/recovery-4.0.0` at:

```text
8609a93ed94932ae9fe9ae2fd7251674e4e2970f
```

CI then passed Gradle tests/build, JAR verification, and artifact generation before `release/4.1.0` was created from that exact commit.

Production resource checks used:

```text
config.yml  ac075e7dea73b34ffb41f974dba20110fe3e1aeb866fadde3bce74ac8caeedb7
tools.yml   72e10e8191fe1510ebcdd5595d2127c864ae6cad9bbc7ba2d7047024dd03c04d
```

The default-tools generator was repaired so an untouched authored 3.6.1 default file deterministically reproduces the production 4.0.0 `tools.yml`.

## Preserved 4.0.0 behavior

4.1.0 does not rebalance or redesign tools. It preserves stable tool/PDC IDs, UUID ownership/world binding, PLAYER/WORLD progression scope, aggregate and per-target progress, one-level-per-action/no-overflow behavior, 100-level defaults, material/enchantment/lore profiles, abilities, activation menus/admin editor, SQLite/WAL persistence, data migration, backups, and natural/player-placed block provenance.

The production natural-block subsystem remains fail-closed while chunk provenance loads asynchronously, and no synchronous database I/O is added to the gameplay progression path.

## 4.1 additions

- optional PlexonCore module registration and health lifecycle
- public immutable PlexonTools API through Bukkit ServicesManager
- exact progress and level-up Bukkit events required by PlexonQuests 3.1.0
- stable event and transaction IDs
- `/pt diagnostics`
- Paper 26.2 / Java 25 build target
- Core-not-shaded distribution verification
- tag-only `v4.1.0` release workflow

## Deployment

1. Stop Paper.
2. Back up `PlexonTools-4.0.0.jar` and the complete `plugins/PlexonTools/` directory.
3. Keep `PlexonCore-1.0.0.jar`, `PlexonQuests-3.1.0.jar`, `PlexonRanks-2.1.0.jar`, and `PlexonKeys-1.2.0.jar` installed when using the full Core ecosystem.
4. Replace only the Tools JAR with `PlexonTools-4.1.0.jar`.
5. Do not delete or regenerate `plugins/PlexonTools/`.
6. Start Paper and run `/plexon modules`, `/plexon diagnostics`, `/quests diagnostics`, and `/pt diagnostics`.
7. Perform one controlled progress action and one controlled level-up test.

Expected Core/Quests state:

```text
PlexonTools READY
PLEXON_TOOLS AVAILABLE
```

## Rollback

Stop Paper, restore the backed-up 4.0.0 JAR, and restore the data-folder backup only if required. 4.1.0 intentionally introduces no irreversible database schema migration.
