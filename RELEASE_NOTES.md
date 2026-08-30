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
- registered items read identity PDC once per event and reuse the validated context through later event phases;
- immutable requirement maps are cached per parsed definition;
- only one pending visual marker is retained per tool UUID and it fetches the newest registry state when flushed;
- block-drop processing exits before item inspection when no enabled tool defines Auto Smelt or Magnet;
- maximum-level events no longer create progress records or database work;
- item PDC/lore and the progress action bar refresh together every four ticks by default;
- level-ups and player lifecycle boundaries flush immediately.

Static lore fragments are cached per immutable level profile, and placeholder rendering now resolves brace/angle placeholders in one scan while preserving MiniMessage tags. The bounded database overflow fallback writes one full snapshot and then ordinary deltas; a failed full write is safely re-queued.

Tune the `1`–`20` tick window in `config.yml`:

```yaml
performance:
  progress-visual-refresh-ticks: 4
```

Periodic asynchronous SQLite persistence remains enabled so a process crash does not discard the complete play session. Bukkit inventory and world mutation also remain on the server thread, where Paper requires them.

## GhostBlocks GUI isolation

- PlexonTools continues to cancel and deny its clicks/drags at `LOWEST` priority.
- Generic arrow controls were replaced with PlexonTools spectral-arrow navigation items.
- A session guard cancels non-Plexon inventory opens for the lifetime of a PlexonTools menu, including replacement menus requested before PlexonTools receives the click callback.
- Back, previous/next page, and level navigation use the same isolated route.
- `/pt all <player>` and `/pt <category> <player>` keep the administrator as the viewer while showing the selected player's state.

## Compatibility

- Paper `1.21.4` and Java `21` remain required.
- SQLite schema version `1` is unchanged; no database migration is needed.
- Existing live YAML files are not overwritten. Current documented defaults are refreshed under `plugins/PlexonTools/examples`.
- Existing per-level reset semantics remain: two consecutive `STONE: 500` requirements each need 500 new breaks.
- The SQLite driver remains bundled; no external database service or library is required.
- Release packaging avoids forced ZIP64; Gradle `check` opens and fully reads the archive while requiring Linux, macOS, and Windows SQLite natives.

See [`docs/PLEXONTOOLS_3_6_1.md`](docs/PLEXONTOOLS_3_6_1.md) for configuration, consolidation rules, tuning, and the upgrade checklist.
