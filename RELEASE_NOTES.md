# PlexonTools 3.6.1

PlexonTools 3.6.1 shares custom-tool progression safely across allowed dimensions, reduces the synchronous work performed during rapid block breaks, and prevents external inventory plugins from replacing PlexonTools navigation.

## Multi-dimension tools

Definitions can now choose their progression boundary:

```yaml
allowed_worlds:
  - Survival_World
  - Survival_World_nether
  - Survival_World_the_end
progression:
  scope: PLAYER
  anchor_world: Survival_World
```

- `PLAYER` keeps one owner/tool UUID and progression state across every allowed world.
- `WORLD` retains independent progress per world.
- The configured anchor-world record wins when older Overworld, Nether, and End copies coexist.
- When no anchor copy exists, the most-progressed copy is retained and rebound to the anchor.
- Duplicate legacy records are deactivated; their counters are not added together, avoiding double-counted activity.
- Existing multi-world definitions without an explicit scope default to `PLAYER`. Existing single-world definitions default to `WORLD` for compatibility.
- The bundled Legendary Pickaxe now explicitly shares progress across `Survival_World`, `Survival_World_nether`, and `Survival_World_the_end`.

## Lower-cost progression visuals

The database was already backed by an in-memory registry, coalesced dirty UUIDs, asynchronous bounded transactions, and a shutdown drain. Those crash-safe guarantees remain unchanged.

Version 3.6.1 instead removes repeated display work from each accepted block event:

- authoritative level and counters update in memory immediately;
- registered items read only identity PDC on the hot path;
- immutable requirement maps are cached per parsed definition;
- only the newest pending visual is retained per tool UUID;
- item PDC/lore and the progress action bar refresh together every four ticks by default;
- level-ups and player lifecycle boundaries flush immediately.

Tune the `1`–`20` tick window in `config.yml`:

```yaml
performance:
  progress-visual-refresh-ticks: 4
```

Periodic asynchronous SQLite persistence remains enabled so a process crash does not discard the complete play session. Bukkit inventory and world mutation also remain on the server thread, where Paper requires them.

## GhostBlocks GUI isolation

- PlexonTools continues to cancel and deny its clicks/drags at `LOWEST` priority.
- Generic arrow controls were replaced with PlexonTools spectral-arrow navigation items.
- A short transition guard cancels non-Plexon inventory opens triggered during a PlexonTools click, including same-tick and next-tick replacement menus.
- Back, previous/next page, and level navigation use the same isolated route.

## Compatibility

- Paper `1.21.4` and Java `21` remain required.
- SQLite schema version `1` is unchanged; no database migration is needed.
- Existing live YAML files are not overwritten. Current documented defaults are refreshed under `plugins/PlexonTools/examples`.
- Existing per-level reset semantics remain: two consecutive `STONE: 500` requirements each need 500 new breaks.
- The SQLite driver remains bundled; no external database service or library is required.
- Release packaging uses ZIP64 and is archive-validated with all SQLite native libraries included.

See [`docs/PLEXONTOOLS_3_6_1.md`](docs/PLEXONTOOLS_3_6_1.md) for configuration, consolidation rules, tuning, and the upgrade checklist.
