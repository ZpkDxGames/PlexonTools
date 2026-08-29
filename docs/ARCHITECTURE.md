# PlexonTools 3.5 architecture

## Activation lifecycle

1. `/pt` resolves the current world's immutable `WorldToolMenu` from `menus.yml`.
2. A click toggles the owner's existing registry record or creates one unique instance on first activation.
3. Deactivation copies the latest item PDC state into memory, marks the record inactive, and removes the physical item.
4. Join, respawn, reload, and world-change reconciliation selects one active instance per tool/world and reconstructs missing items.
5. Leaving the bound world removes the item without changing its active entitlement; returning restores it when inventory space exists.

The activation registry—not an item entity on the ground—is the recovery source. A full inventory delays materialization and never drops a protected tool.

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

- `WorldToolMenu` controls the player-facing world title, layout, filler, reservations, and slots.
- `ToolCategory` remains internal organization and explicit legacy-showcase metadata.
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

The materialized item remains the gameplay/progression source of truth. `data.yml` is also the authoritative activation entitlement and recovery snapshot when the item is deactivated or temporarily absent.

## Asynchronous persistence

Every registry mutation increments a revision counter. The async checkpoint task returns immediately if no revision changed or another write is active. A stable record copy is serialized to `data.yml.tmp`, then moved over `data.yml` atomically when supported. If events change the cache during a write, the persisted revision remains behind and the next scheduled checkpoint captures the newer state.

## Configuration safety

`ToolConfigRepository`, `CategoryRepository`, and `WorldMenuRepository` clone their active YAML before an in-game mutation. They apply the change, strictly parse the candidate, save it, and only then swap the runtime cache. Invalid input leaves the previous file/cache active. Manual reload parsing logs and skips individual invalid entries; startup fails safely if no valid category exists.

The parser accepts 2.0 list-filter and material-upgrade aliases. Missing item category PDC is treated as a legacy item and synchronized from its current definition on the next accepted refresh.

Registry schema v4 adds `active` and `menu_managed`. Missing fields from a v3 record default to `true` and `false`, respectively: already-issued tools remain active, while world-menu reservations only revoke instances that a player has actually managed through that menu. Loading an older schema marks the registry dirty so the next checkpoint materializes both fields. The item profile fingerprint includes the 3.5 clean-tooltip revision, so existing items receive permanent unbreakable and hidden-tooltip flags on their next reconciliation.

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

Paper events, PDC mutation, inventory mutation, abilities, GUIs, and registry map updates run on the server thread. Only immutable registry snapshots are serialized by the async scheduler. Configuration file editing occurs only on low-frequency administrator control paths, never on gameplay progression paths.
