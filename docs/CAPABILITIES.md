# PlexonTools 3.0 capabilities

## Platform

- Paper API `1.21.4-R0.1-SNAPSHOT`
- Java `21`
- Adventure MiniMessage and Paper PDC
- YAML configuration and audit snapshots
- No runtime dependencies, NMS, or CraftBukkit imports

All text enters Adventure through one parser that prefixes `<!italic>`. This applies to item names, lore, chat feedback, action bars, and inventory titles.

## Unique progressive items

Each grant creates a new instance UUID and binds the item to an owner and one allowed world. The item tracks its level, current aggregate counter, optional target breakdown, category, and last applied profile fingerprint. Definitions can change an item's name, material, enchantments, lore, unbreakable state, glint, hidden flags, custom model data, and abilities at every level.

Compatible overflow passes into the next requirement. GENERAL overflow passes as a total, while SPECIFIC overflow is retained by target and only passes when the next level accepts that target.

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

Names and materials inherit from the most recent earlier override. Enchantments, lore, item flags, and abilities are complete states for their level. `material_upgrade` remains an alias for `material`.

The built-in lore exposes identity, category, progress, quota detail, owner, world, material, and enchantment placeholders. At maximum level, `required` and `next_level` render as `MAX`.

## Commands and permissions

| Command | Permission |
|---|---|
| `/pt`, `/pt <category>`, `/pt all` | `plexontools.use` |
| Target-player category/all routes | `plexontools.admin` |
| `/pt give <player> <tool_id> [world]` | `plexontools.give` |
| `/pt gui` | `plexontools.gui` |
| `/pt reload` | `plexontools.reload` |

`plexontools.bypass.owner` and `plexontools.bypass.world` bypass their narrow enforcement checks. `plexontools.admin` includes all child permissions.

## Files

| File | Purpose |
|---|---|
| `config.yml` | Enforcement, effects, progress bars, GUI titles, and default lore |
| `categories.yml` | Category names, icons, slots, and descriptions |
| `tools.yml` | Tool definitions, requirements, profiles, and abilities |
| `messages.yml` | MiniMessage feedback |
| `data.yml` | Generated asynchronous instance audit snapshot |
