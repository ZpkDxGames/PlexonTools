# PlexonTools 3.6.0 capabilities

## Platform

- Paper API `1.21.4-R0.1-SNAPSHOT`
- Java `21`
- Adventure MiniMessage and Paper PDC
- Administrator-authored YAML plus generated SQLite runtime state
- Bundled SQLite driver; no external database service, NMS, or CraftBukkit imports

All text enters Adventure through one parser that prefixes `<!italic>`. This applies to item names, lore, chat feedback, action bars, and inventory titles.

## Unique progressive items

Each first activation or direct grant creates a unique instance UUID and permanently binds it to one owner and world. The registry retains its active state, level, aggregate counter, optional target breakdown, category, and timestamps even while the physical item is deactivated. Definitions can still change an item's name, material, enchantments, lore, glint, custom model data, and abilities at every level.

All custom tools are permanently unbreakable and clean-tooltip protected. They cannot be manually dropped, transferred into external inventories, lost on death, picked up by another player, or used by a non-owner. Leaving a bound world temporarily removes active tools and returning restores them.

Progress is isolated per level. Completing a level resets both the GENERAL total and SPECIFIC target map to zero, and excess activity from the completing event is discarded. One event can advance at most one level, so repeated quotas must be completed independently.

Every accepted requirement event displays the current level, progress bar, credited amount, requirement, and percentage in the action bar by default. Administrators can disable this with `effects.progress-action-bar` or customize `messages.progress-update`.

## Tracking types

### `BLOCKS_BROKEN`

Counts accepted `BlockBreakEvent` activity. GENERAL accepts all blocks; SPECIFIC accepts selected block materials. Cancelled events and unauthorized tools are ignored. Matching player-placed blocks count.

### `MOBS_KILLED`

Counts living entities whose killer is the authorized holder. SPECIFIC targets use living `EntityType` names.

### `ITEMS_FARMED`

Counts supported mature crops broken normally and harvest-without-breaking events such as berry harvesting. Supported targets are Wheat, Carrots, Potatoes, Beetroot, Nether Wart, Cocoa, Sweet Berry Bush, Melon, Pumpkin, Sugar Cane, Cactus, Bamboo, and Kelp.

### `FISH_CAUGHT`

Counts actual Cod, Salmon, Tropical Fish, and Pufferfish caught with an authorized tool. Junk and treasure catches do not advance fish requirements.

### `DAMAGE_DEALT`

Adds the rounded final damage dealt by the player to a living entity after cancellation and mitigation. SPECIFIC mode can isolate entity types.

### `BLOCKS_PLACED`

Counts successful placements from the event hand. This supports progressive placeable builder items, including material upgrades between levels.

## Abilities

| Ability | Behavior |
|---|---|
| `AUTO_SMELT` | Replaces supported raw ore/ore drops with their furnace result while preserving stack amount |
| `AREA_MINE_3X3` | Breaks an orientation-aware 3×3 plane for pickaxes, shovels, and axes; containers and protected/unbreakable blocks are skipped |
| `EXP_BOOSTER` | Multiplies mining, entity-death, and fishing EXP with a validated `1.0`–`100.0` multiplier |
| `MOB_POTION_EFFECT` | Applies a registry-validated effect to the holder or hit target with configurable level and duration |
| `MAGNET` | Moves block, entity, fishing, and harvest drops into the holder's inventory while preserving leftovers |

Abilities are exact per-level maps. Enabling one adds safe defaults; configuration bounds prevent invalid multipliers, amplifiers, or hour-long-plus durations.

## Requirement configuration

Root tracking values provide defaults:

```yaml
tracking:
  type: ITEMS_FARMED
  mode: SPECIFIC
  targets:
    WHEAT: 250
    CARROTS: 250
```

Each level can override them:

```yaml
levels:
  1:
    requirement_mode: SPECIFIC
  2:
    requirement_mode: GENERAL
    requirement: 1500
```

An empty SPECIFIC map intentionally cannot complete. GUI mode changes preserve the combined total when moving to GENERAL and start an empty target picker when moving to SPECIFIC.

## World activation menus

```yaml
worlds:
  survival_world:
    title: "<gradient:#FFF176:#FF8F00><bold>Mining Loadout</bold></gradient> <dark_gray>• {world}</dark_gray>"
    rows: 3
    filler:
      material: BLACK_STAINED_GLASS_PANE
      name: " "
    tools:
      legendary_pickaxe:
        slot: 13
```

World menus accept three to six rows. Pinned tool slots must be unique inner content slots. A tool is available only when its definition is enabled and the same world appears in its `allowed_worlds` list.

By default, enabled definitions appear automatically when the current world is in `allowed_worlds`; `menus.yml` entries only pin exact card positions. `world-menu.auto-show-allowed-tools: false` enables strict membership. Automatic placement reserves the slot beneath each card for its ON/OFF panel when possible, and a blocked card remains directly clickable.

`config.yml` provides default MiniMessage templates for the tool-card display name/lore and both panel states. Their placeholders include the normal item-profile values plus `status`, `state`, `state_symbol`, `toggle_action`, and `toggle_hint`. The same values are editable through `/pt gui` → **Player Menu Appearance**.

## Category configuration

```yaml
categories:
  mining:
    display_name: "<gradient:#FF512F:#DD2476><bold>Mining Tools</bold></gradient>"
    icon: DIAMOND_PICKAXE
    slot: 11
    description:
      - "<gray>Progressive resource tools.</gray>"
```

IDs use lowercase letters, numbers, underscores, or hyphens. Names and descriptions support MiniMessage. Slots must be unique.

## Item profiles and lore

Names and materials inherit from the most recent earlier override. Enchantments, lore, glint, model data, and abilities are complete states for their level. `material_upgrade` remains an alias for `material`. Legacy unbreakable/hidden flag values remain readable but 3.5 always applies unbreakable and clean-tooltip protection.

The global `tool-lore.template` is a freely ordered list exposing identity, category, progress, quota detail, owner, world, material, and enchantment placeholders. `{requirement_lines}` produces one independently formatted row per SPECIFIC quota or one GENERAL row, using `requirement_goal`, `requirement_target`, `requirement_current`, `requirement_required`, `requirement_remaining`, and `requirement_percentage`. At maximum level, its separate maximum row is used and `required` and `next_level` render as `MAX`.

A root `lore` list in a tool definition overrides the global template; an individual level list overrides both. An explicit empty list removes lore at that scope. Older `default_lore_format` values remain fallback-compatible.

## Commands and permissions

| Command | Permission |
|---|---|
| `/pt` world activation menu | `plexontools.use` |
| Legacy `/pt <category>` and `/pt all` showcase routes | `plexontools.use` |
| Target-player category/all routes | `plexontools.admin` |
| `/pt give <player> <tool_id> [world]` | `plexontools.give` |
| `/pt gui` | `plexontools.gui` |
| `/pt reload` | `plexontools.reload` |
| `/pt backup` | `plexontools.backup` |

Owner binding cannot be bypassed in 3.5. `plexontools.bypass.world` retains its narrow use-check bypass; activation and inventory materialization remain world-scoped. `plexontools.admin` includes all administrative child permissions.

## Files

| File | Purpose |
|---|---|
| `config.yml` | Enforcement, SQLite tuning, effects, progress bars, player-menu templates, and global item lore |
| `menus.yml` | Per-world `/pt` layouts and exact tool-slot pins |
| `categories.yml` | Category names, icons, slots, and descriptions |
| `tools.yml` | Tool definitions, requirements, profiles, and abilities |
| `messages.yml` | MiniMessage feedback |
| `plexontools.db` | Generated transactional activation and instance recovery database |
| `examples/*.yml` | Refreshed format references that never overwrite live YAML |

Schema-v3/v4 `data.yml` is accepted only as a one-time 3.6 migration source. Normal gameplay updates remain in memory and are coalesced into asynchronous bounded SQLite transactions.
