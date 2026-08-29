# PlexonTools 3.0 architecture

## Gameplay flow

1. A listener reads the held/event-hand item's PDC into an immutable `ToolState`.
2. The ID resolves against the cached immutable `ToolDefinition` map.
3. `ProgressionService` validates the configured level, owner, allowed world, and instance-bound world.
4. The tracking listener converts the accepted event into a normalized material/entity target and long increment.
5. `RequirementProgression` updates the GENERAL total or SPECIFIC target map and carries compatible overflow through contiguous levels.
6. `ToolItemService` refreshes dynamic text/PDC or reapplies the complete level profile after a level or fingerprint change.
7. `InstanceRegistry` updates a concurrent in-memory audit record and marks the snapshot dirty.

There is no YAML lookup or disk write in block, combat, farming, fishing, damage, or placement progression. A scheduled asynchronous task copies the concurrent registry and writes `data.yml`; shutdown performs a final checkpoint after event handling stops.

## Definition graph

- `ToolCategory` controls player navigation metadata.
- `ToolDefinition` contains identity, category, world allowlist, tracking type, and ordered levels.
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
| `bound_world` | String | Immutable grant-time world binding |
| `owner` | String | Owner UUID |
| `stat_breakdown` | String | Compact normalized SPECIFIC counters |
| `profile_hash` | Integer | Last applied profile fingerprint |

The item is the runtime source of truth. `data.yml` is an audit index and is not queried by progression except to obtain a last-known owner name for warnings.

## Asynchronous persistence

Every registry mutation increments a revision counter. The async checkpoint task returns immediately if no revision changed or another write is active. A stable record copy is serialized to `data.yml.tmp`, then moved over `data.yml` atomically when supported. If events change the cache during a write, the persisted revision remains behind and the next scheduled checkpoint captures the newer state.

## Configuration safety

`ToolConfigRepository` and `CategoryRepository` clone their active YAML before an in-game mutation. They apply the change, strictly parse the candidate, save it, and only then swap the runtime cache. Invalid input leaves the previous file/cache active. Manual reload parsing logs and skips individual invalid entries; startup fails safely if no valid category exists.

The parser accepts 2.0 list-filter and material-upgrade aliases. Missing item category PDC is treated as a legacy item and synchronized from its current definition on the next accepted refresh.

## Ability integration

- Auto Smelt and block Magnet modify Paper's mutable `BlockDropItemEvent` entities.
- EXP Booster changes the exposed EXP values on block-break, entity-death, and fishing events.
- Potion effects resolve through Paper/Bukkit registries without implementation classes.
- Area Mine calculates a plane from the player's look vector, emits a cancellable block-break check per adjacent block, derives drops with the Paper block API, and uses a recursion guard.
- Magnet keeps overflow drops in the event/world when the inventory is full.

## Threading model

Paper events, PDC mutation, inventory mutation, abilities, GUIs, and registry map updates run on the server thread. Only immutable registry snapshots are serialized by the async scheduler. Configuration file editing occurs only on low-frequency administrator control paths, never on gameplay progression paths.
