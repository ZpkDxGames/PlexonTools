# PlexonTools 3.6.1 architecture

## Activation lifecycle

1. `/pt` selects enabled definitions whose `allowed_worlds` contains the current world; optional strict mode also requires membership in its immutable `WorldToolMenu`.
2. Explicit `menus.yml` entries pin cards to exact slots, while unpinned available tools use panel-aware automatic placement.
3. A click on the card or its panel toggles the owner's canonical registry record or creates one unique instance on first activation.
4. `PLAYER` scope selects one owner/tool record across every allowed world and anchors its persisted binding; `WORLD` scope selects one record per owner/tool/world.
5. Deactivation marks the in-memory record inactive and removes the physical item without replacing newer cached progress with older item PDC.
6. Join, respawn, reload, and world-change reconciliation consolidates legacy shared copies, selects the canonical active instance, and reconstructs missing items.

The activation registry—not an item entity on the ground—is the recovery source. A full inventory delays materialization and never drops a protected tool.

## Gameplay flow

1. A listener reads the held/event-hand item's small identity PDC and resolves the latest immutable `ToolState` from the in-memory registry; full PDC progress parsing is only a legacy fallback.
2. The ID resolves against the cached immutable `ToolDefinition` map.
3. `ProgressionService` validates the configured level, owner, allowed world, and instance-bound world.
4. The tracking listener converts the accepted event into a normalized material/entity target and long increment.
5. `RequirementProgression` updates the GENERAL total or SPECIFIC target map; completing a level advances once and resets both counters without carrying overflow.
6. `InstanceRegistry` immediately replaces the authoritative cached record and coalesces the changed UUID into the asynchronous persistence queue.
7. Non-boundary events retain one pending visual marker per instance. A short server-thread task fetches newest registry state, applies PDC/lore once, and sends the configured action bar; rapid events do not allocate or replace visual markers repeatedly.
8. Level-ups and lifecycle boundaries flush visual state immediately, and a changed level/profile reapplies the complete item profile.

There is no YAML lookup or database I/O in block, combat, farming, fishing, damage, or placement progression. A scheduled asynchronous task writes coalesced UUID updates to SQLite in bounded transactions; shutdown drains the queue and checkpoints the WAL after event handling stops.

## Definition graph

- `WorldToolMenu` controls the player-facing world title, layout, filler, and exact slot pins.
- `PluginSettings` owns the default tool-card and toggle-panel templates plus automatic/strict membership mode.
- `ToolCategory` remains internal organization and explicit legacy-showcase metadata.
- `ToolDefinition` contains identity, category, world allowlist, progression scope/anchor, tracking type, and ordered levels.
- `ToolLevel` is the resolved item/requirement/ability profile for one numeric level.
- `LevelRequirement` models GENERAL totals or SPECIFIC quotas.
- `ToolAbilitySettings` validates optional multiplier and potion parameters.
- `ToolState` contains only per-instance runtime values.

All collections crossing the runtime boundary are copied into immutable maps, lists, sets, or navigable maps.

## PDC schema

All keys use the `plexontools` namespace:

| Key | Type | Purpose |
|---|---|---|
| `id` | String | Definition ID |
| `uuid` | String | Unique instance UUID |
| `level` | Integer | Current level |
| `stat_count` | Long | Aggregate/raw current-level progress |
| `category` | String | Current definition category |
| `bound_world` | String | Per-world binding, or canonical anchor for shared-player progression |
| `owner` | String | Owner UUID |
| `stat_breakdown` | String | Compact normalized SPECIFIC counters |
| `profile_hash` | Integer | Last applied profile fingerprint |

The concurrent registry record is authoritative during runtime. The materialized item's PDC is a coalesced portable snapshot, and `plexontools.db` is the durable activation entitlement and recovery source when an item is deactivated or temporarily absent.

## SQLite persistence

Every registry mutation increments a revision counter and records the affected instance UUID in an insertion-ordered, coalescing queue. The asynchronous worker takes stable immutable record copies and executes prepared upserts in bounded transactions. An acknowledgment removes a UUID only when its queued revision still matches the written revision; an update that races with a write therefore remains pending for the next batch.

If the number of distinct pending UUIDs reaches the configured bound, the queue switches to a safe full-snapshot marker instead of retaining unbounded keys or discarding state. Once that snapshot starts, newer mutations enter the ordinary delta queue, so continuous activity does not force the same full registry to be written repeatedly. A failed snapshot restores the full marker before reporting the database error. Shutdown and `/pt backup` drain the same queue under the database lock. Backup then checkpoints WAL and copies the main database file.

SQLite schema version 1 separates `players`, `tool_instances`, and normalized `target_progress`, with migration information in `schema_metadata`. Foreign keys and constraints protect parent/child identity. Owner, tool, owner/tool/world, and active-owner/world query paths are indexed. Startup requests WAL, applies a busy timeout, optionally runs `PRAGMA integrity_check`, and fails with an actionable error rather than silently accepting malformed state.

An existing schema-v3/v4 `data.yml` is strictly parsed before a missing database is created. PlexonTools preserves a timestamped source backup, imports and verifies all state transactionally, then writes a source hash and completion marker. The original YAML is historical after migration and is never re-imported once marked complete.

## Configuration safety

`ToolConfigRepository`, `CategoryRepository`, and `WorldMenuRepository` clone their active YAML before an in-game mutation. They apply the change, strictly parse the candidate, save it, and only then swap the runtime cache. Invalid input leaves the previous file/cache active. Manual reload parsing logs and skips individual invalid entries; startup fails safely if no valid category exists.

The parser accepts 2.0 list-filter and material-upgrade aliases. Missing item category PDC is treated as a legacy item and synchronized from its current definition on the next accepted refresh.

Legacy registry schema v4 added `active` and `menu_managed`. During the one-time SQLite import, missing fields from a v3 record default to `true` and `false`, respectively: already-issued tools remain active, while menu-managed instances are revoked when their definition is disabled or their bound world is removed from `allowed_worlds`. In optional strict mode, removing the corresponding menu pin also revokes them. The item profile fingerprint includes the 3.5 clean-tooltip revision, so existing items receive permanent unbreakable and hidden-tooltip flags on their next reconciliation.

## Protection lifecycle

- Drop events are cancelled for every tagged tool.
- Death removes tagged tools from normal drops and adds them to Paper's keep-item collection when keep-inventory is disabled.
- External inventory shift-clicks, cursor placement, hotbar swaps, drags, and creative cloning are rejected.
- Foreign-owner pickup and use are rejected unconditionally.
- Item damage is cancelled in addition to the unbreakable metadata flag.

## Ability integration

- Auto Smelt and block Magnet modify Paper's mutable `BlockDropItemEvent` entities.
- EXP Booster changes the exposed EXP values on block-break, entity-death, and fishing events.
- Potion effects resolve through Paper/Bukkit registries without implementation classes.
- Area Mine calculates a plane from the player's look vector, emits a cancellable block-break check per adjacent block, derives drops with the Paper block API, and uses a recursion guard.
- Magnet keeps overflow drops in the event/world when the inventory is full.

## Threading model

Paper events, PDC mutation, inventory mutation, abilities, GUIs, and registry map updates run on the server thread because Bukkit inventory/world APIs are not generally safe to mutate asynchronously. Expensive item metadata/lore rendering and action-bar output are coalesced per UUID; only immutable record copies and JDBC work run through the asynchronous persistence task. Configuration file editing occurs only on low-frequency administrator control paths, never on gameplay progression paths.

PlexonTools inventory clicks and drags are claimed at `LOWEST` priority and denied immediately. Navigation uses namespaced PDC actions and spectral arrows. A session guard rejects non-Plexon inventory opens at `HIGHEST` priority for as long as a PlexonTools holder is active, with a short transition token covering close/open races.
