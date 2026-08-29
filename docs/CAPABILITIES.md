# PlexonTools 2.0 — Capabilities and Customization Reference

PlexonTools is a Paper-native Minecraft plugin for creating progressive, individually tracked custom tools. Administrators can define tools such as pickaxes, axes, shovels, swords, or other item-based equipment that record player activity and evolve through configurable levels.

Each granted tool is a unique physical instance with its own owner, authorized world, level, and progression data. Tool definitions can be managed through YAML files or the in-game administrative GUI.

## Platform and requirements

- Paper `1.21.4`
- Java `21`
- Gradle build system
- No runtime dependencies
- Native Adventure components and MiniMessage formatting
- Paper Persistent Data Container storage for item state
- YAML-backed definitions, settings, messages, and instance registry
- No NMS or CraftBukkit implementation imports

## Core capabilities

### Progressive custom tools

A Plexon tool can:

- Track blocks broken or living entities killed.
- Advance through any number of contiguous levels.
- Change its material when reaching selected levels.
- Change its display name at any level.
- Apply a complete enchantment profile per level.
- Apply independently formatted lore per level.
- Change unbreakable state, glint, hidden flags, and custom model data.
- Carry compatible excess progress into following levels.
- Continue recording matching activity at its maximum configured level.

A new physical tool begins at level 1. A level's requirement describes the work needed to advance from that level to the next.

### Unique item identity

Every granted tool receives:

- A definition ID matching its entry in `tools.yml`.
- A unique instance UUID.
- A current level.
- An aggregate current-level stat counter.
- An immutable bound world.
- An owner UUID.
- Optional per-target progress data for SPECIFIC requirements.
- A profile fingerprint used to synchronize configuration changes.

Two items created from the same definition remain separate instances and maintain independent progression.

## Requirement engine

Requirements can be configured independently for each level.

### GENERAL mode

GENERAL mode uses one shared counter for all activity of the configured tracking type.

Examples:

- Break any 1,000 blocks.
- Kill any 200 living entities.

```yaml
tracking:
  type: BLOCKS_BROKEN
  mode: GENERAL
  amount: 1000
```

Native GENERAL mode does not filter individual materials or entities. Every valid event of the selected tracking type contributes to the same total.

### SPECIFIC mode

SPECIFIC mode maintains an independent counter for every selected target. All configured quotas must be completed before the tool advances.

Examples:

- Break 500x Stone and 200x Deepslate.
- Kill 50x Wither Skeleton.

```yaml
tracking:
  type: BLOCKS_BROKEN
  mode: SPECIFIC
  targets:
    STONE: 500
    DEEPSLATE: 200
```

Progress is capped per target when calculating the displayed aggregate percentage. Raw excess is retained so compatible target overflow can carry into the next level.

### Per-level overrides

Root tracking values act as defaults. Individual levels may override the mode and requirement values.

```yaml
levels:
  1:
    requirement_mode: SPECIFIC
  2:
    requirement_mode: SPECIFIC
    requirements:
      STONE: 1000
      DEEPSLATE: 400
  3:
    requirement_mode: GENERAL
    requirement: 3000
```

This allows one tool to use different progression strategies throughout its lifetime.

## Supported tracking types

### Blocks broken

`BLOCKS_BROKEN` listens for accepted block-break events performed while the player is holding the Plexon tool.

- GENERAL mode counts every block type.
- SPECIFIC mode counts only selected Bukkit materials.
- Cancelled events are ignored.
- Unauthorized progression can optionally cancel the block break.

Block tracking is material-based. Matching player-placed blocks count because PlexonTools 2.0 does not store block-origin history.

### Mobs killed

`MOBS_KILLED` records living entities killed by the player while the Plexon tool is held.

- GENERAL mode counts every living entity type.
- SPECIFIC mode counts only selected Bukkit entity types.
- Owner and world authorization are checked before progression.
- Unauthorized attempts display the configured action-bar warning.

## Per-level item customization

Each level is a complete item profile.

### Display names

- MiniMessage formatting is supported.
- Gradients, colors, decorations, and dynamic placeholders are supported.
- A level may declare a new display name.
- When omitted, the most recent earlier display name is inherited.
- The root `display_name` is the initial fallback.

### Materials and tier changes

- Any valid Bukkit item material can be used as the base material.
- A level may change the physical item material.
- Later profiles inherit the most recent material change.
- Structural level operations preserve resolved material tiers.
- The legacy `material_upgrade` key remains accepted.

Example progression:

1. Iron Pickaxe
2. Diamond Pickaxe
3. Netherite Pickaxe

### Enchantments

- Every level has its own complete enchantment map.
- The GUI displays Paper's enchantment registry in a paginated browser.
- Enchantment levels can range from 1 through 255.
- Unsafe levels are applied intentionally for custom tool designs.
- Bulk text input is also supported.

Visual controls:

| Input | Operation |
|---|---|
| Left-click | Increase by 1 |
| Shift-left-click | Increase by 5 |
| Right-click | Decrease by 1 |
| Shift-right-click | Remove the enchantment |

### Lore

- Every level may use a complete custom lore list.
- Lore is parsed as MiniMessage.
- Lines can be added, edited, deleted, moved, cleared, or replaced in bulk.
- Both `{placeholder}` and `<placeholder>` aliases are supported.
- Missing level lore uses `default-lore-format.lines` from `config.yml`.
- Empty lore is supported.

### Item properties

Each level may configure:

- Unbreakable state.
- Enchantment glint: `AUTO`, `ON`, or `OFF`.
- Hidden enchantment text.
- Hidden attribute text.
- Custom model data.

## Standard lore and placeholders

The default Plexon lore displays:

- Plexon Tool header.
- Gradient dividers.
- Current level.
- Human-readable objective.
- Current and required progress.
- Percentage.
- Procedural progress bar.
- Authorized world.
- Owner name.

Available placeholders include:

### Identity

- `tool`
- `tool_id`
- `level_name`
- `uuid`

### Progress

- `level`
- `max_level`
- `next_level`
- `current`
- `current_xp`
- `required`
- `required_xp`
- `remaining`
- `percent`
- `percentage`
- `total`
- `bar`
- `progress_bar`

### Requirement information

- `requirement_mode`
- `goal_type_description`
- `target_progress`
- `tracking`
- `targets`

### Ownership and world binding

- `world`
- `bound_world`
- `owner`
- `owner_name`
- `owner_uuid`

### Item profile

- `material`
- `enchantments`

At the maximum level, `required` and `next_level` render as `MAX`.

## Administrative GUI

Open the editor with `/pt gui`.

### Tool management

Administrators can:

- Create a new tool from the held item material.
- Enable or disable a definition.
- Edit its root MiniMessage name.
- Change its base material.
- Select allowed worlds.
- Add an unloaded world by exact name.
- Switch between block and mob tracking.
- Open the requirement engine.
- Manage all level profiles.
- Preview the resolved item.
- Grant a test instance to themselves.
- Delete a definition using confirmation controls.

### Requirement editor

The requirement editor provides:

- GENERAL/SPECIFIC mode toggle.
- Requirement summary and combined total.
- `+1`, `+10`, `+100`, and `+1000` controls.
- `-1`, `-10`, `-100`, and `-1000` controls.
- Exact positive-number entry through chat.
- Searchable, paginated target selection.
- Per-target amount editor.
- Quick target removal.

Switching from SPECIFIC to GENERAL preserves the combined required total. Switching from GENERAL to SPECIFIC begins with an empty target set so the administrator can define exact quotas.

### Target selector

- Lists valid block materials or living entity types.
- Uses block icons and spawn eggs when available.
- Supports substring search such as `ORE`, `DEEPSLATE`, or `SKELETON`.
- Marks selected targets and displays their current quotas.
- Adds a new target with a default amount of 100.
- Opens an exact amount editor for existing targets.

### Level management

Administrators can:

- Add a new level by cloning the final profile.
- Double GENERAL totals or every SPECIFIC quota when adding a level.
- Duplicate any profile directly after its source.
- Move a profile one position earlier or later.
- Delete any level while retaining at least one profile.
- Automatically renumber every later level.
- Navigate directly between adjacent profiles.
- Preview every fully resolved profile.

Name and material inheritance are recalculated after structural edits so moving or deleting an earlier override does not unintentionally alter later resolved profiles.

## Player showcase GUI

Running `/pt` opens the player-facing showcase.

The showcase can display:

- Tools enabled for player access.
- Availability in the player's current world.
- Locked-world state.
- The best owned instance currently in the player's inventory.
- Current level and progress.
- Dynamic progress bar.
- Next-level name, material, and enchantment rewards.
- Maximum-level status.

## Commands and permissions

| Command | Permission | Function |
|---|---|---|
| `/pt` | `plexontools.use` | Open the player showcase |
| `/pt give <player> <tool_id>` | `plexontools.give` | Grant a unique tool instance |
| `/pt gui` | `plexontools.gui` | Open the administrative editor |
| `/pt reload` | `plexontools.reload` | Reload settings, messages, and definitions |

Command aliases:

- `/plexontool`
- `/plexontools`

Administrative and bypass permissions:

| Permission | Function |
|---|---|
| `plexontools.admin` | Grants every administrative capability |
| `plexontools.bypass.world` | Bypasses world restrictions |
| `plexontools.bypass.owner` | Bypasses owner restrictions |

## World and ownership enforcement

PlexonTools uses two layers of world protection:

1. The definition's `allowed_worlds` list.
2. The immutable world assigned to the individual instance when granted.

With default settings, both layers and the owner UUID must match before the tool can be used or gain progression.

Configurable enforcement options include:

- Bound-world validation.
- Owner validation.
- Cancelling unauthorized block breaks.
- Cancelling unauthorized interactions.
- Cancelling unauthorized attacks.
- Action-bar warning cooldown.
- World and owner bypass permissions.

## Persistence and storage

### Item PDC

Runtime state is stored directly on each `ItemStack`.

Mandatory keys:

- `plexontools:id`
- `plexontools:uuid`
- `plexontools:level`
- `plexontools:stat_count`
- `plexontools:bound_world`

Additional keys:

- `plexontools:stat_breakdown`
- `plexontools:owner`
- `plexontools:profile_hash`

`stat_breakdown` is a compact optional mapping used by SPECIFIC requirements. Items created by beta versions do not require this key to remain readable.

### Instance registry

`data.yml` stores an audit registry keyed by instance UUID, including:

- Tool ID.
- Owner UUID and last known name.
- Bound world.
- Current level and aggregate progress.
- Lifetime accepted activity.
- Creation and update timestamps.

The registry is cached in memory and saved periodically or during shutdown.

## Configuration files

| File | Purpose |
|---|---|
| `config.yml` | Enforcement, GUI, effects, progress bar, and default lore |
| `tools.yml` | Tool definitions and complete level profiles |
| `messages.yml` | MiniMessage feedback and warnings |
| `data.yml` | Generated instance audit registry |

GUI changes are transactional. PlexonTools clones the current configuration, applies the requested mutation, parses every definition, and writes the file only if the complete result is valid.

Manual reloads are fault-tolerant: invalid manually authored definitions are logged and skipped while valid definitions remain available.

## Effects and feedback

PlexonTools supports:

- Configurable level-up sound.
- Optional level-up particles.
- Chat and action-bar MiniMessage feedback.
- Level-up announcements with dynamic tool and level values.
- Invalid-item warnings.
- Owner-lock warnings.
- World-lock warnings.
- Inventory-full handling that drops the granted item safely at the player's feet.

## Performance behavior

The progression hot path is designed to avoid disk access.

- Definitions are cached as immutable in-memory objects.
- No YAML or database write occurs for each block break or mob kill.
- Progress changes update item PDC in memory.
- Lore is reserialized only after accepted progress changes, level changes, or profile synchronization.
- The instance registry is flushed on a configurable interval.
- Counter arithmetic saturates at `Long.MAX_VALUE` rather than overflowing.
- Profile fingerprints avoid unnecessary full item reapplication while still synchronizing configuration changes.

## Beta compatibility

PlexonTools 2.0 accepts beta.1 and beta.2 data without a forced rewrite.

- Existing mandatory item PDC keys remain unchanged.
- Items without `stat_breakdown` use an empty breakdown.
- Items without `profile_hash` receive a full profile refresh after their next accepted progress change.
- Legacy `tracking.targets` lists keep their original shared filtered-total behavior.
- Native 2.0 `tracking.targets` maps use independent SPECIFIC quotas.
- Legacy `material_upgrade` values remain supported.
- Existing `data.yml` records remain readable.

The plugin does not replace existing configuration files with bundled defaults during startup.

## Current limitations and behavioral notes

- Block origin is not tracked. Matching player-placed blocks count.
- Tool definitions currently support `BLOCKS_BROKEN` and `MOBS_KILLED` tracking.
- A SPECIFIC level with no targets cannot progress until a target is configured.
- Configuration changes can reinterpret existing item progress when switching an issued tool between GENERAL and SPECIFIC modes because historical aggregate progress cannot be reconstructed into individual target counters.
- Existing physical items synchronize their resolved profile after their next accepted progress change rather than through a global inventory scan.

## Typical administration workflow

1. Run `/pt gui`.
2. Create a tool using the desired held material.
3. Configure allowed worlds and tracking type.
4. Select GENERAL or SPECIFIC requirements.
5. Configure each level's amount or target quotas.
6. Customize the name, material, enchantments, lore, and item properties for each level.
7. Preview the resolved profiles.
8. Grant a test instance.
9. Verify progression in an authorized world.
10. Enable the definition for player access.

For YAML examples and upgrade instructions, see the project `README.md`. For detailed GUI input behavior, see `docs/ADMIN_EDITOR.md`. For implementation and persistence details, see `docs/ARCHITECTURE.md`.
