# PlexonTools

PlexonTools is a Paper-native progression engine for unique, world-activated custom tools. Every tool has its own UUID, permanent owner, world binding, activation state, level, aggregate progress, and optional per-target counters.

> **Current release:** `3.5.0` — **Creator:** Tonim (`ZpkDxGames`)

## Requirements

- Paper `1.21.4`
- Java `21`
- No runtime dependencies
- No NMS or CraftBukkit implementation access

## 3.5 highlights

- `/pt` is now a configurable per-world activation menu instead of a category browser.
- Admins choose each world's title, rows, filler, reserved tools, and exact tool slots through `/pt gui` or `menus.yml`.
- Players can activate and deactivate a reserved tool without losing its UUID, level, or progress.
- Active tools leave the inventory outside their bound world and safely return on join, respawn, or re-entry.
- Bound tools are always unbreakable, owner-only, non-droppable, retained on death, and blocked from external inventories.
- SPECIFIC objectives render one requirement per lore line; enchantments, attributes, unbreakable text, and additional vanilla details are hidden.
- Six tracking types: blocks broken, mobs killed, items farmed, fish caught, damage dealt, and blocks placed.
- GENERAL shared totals and SPECIFIC per-target quotas on every level, with compatible overflow carried forward.
- Per-level Auto Smelt, protected-aware 3×3 mining, EXP Booster, potion effect, and Magnet abilities.
- An in-game dashboard for world menus, tools, internal categories, global settings, requirements, levels, and abilities.
- `<!italic>` normalization for every MiniMessage deserialization, including names, lore, messages, and GUIs.
- Item PDC mutations and progression calculations stay in memory; the audit registry checkpoints asynchronously.
- Backward-compatible loading for 2.0 definitions, issued items, list filters, and `data.yml` records.

## Installation

1. Download `PlexonTools-3.5.0.jar` from the GitHub release.
2. Place it in the Paper server's `plugins` directory.
3. Start the server once to generate `config.yml`, `messages.yml`, `menus.yml`, `categories.yml`, `tools.yml`, and `data.yml`.
4. Customize through `/pt gui` or YAML, then run `/pt reload`.

Build from source with Java 21 and `gradle clean build`.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/pt` | `plexontools.use` | Open the current world's tool activation menu |
| `/pt <category> [player]` | `plexontools.use`; target requires `plexontools.admin` | Open one category |
| `/pt all [player]` | `plexontools.use`; target requires `plexontools.admin` | Open the unified showcase |
| `/pt give <player> <tool_id> [world]` | `plexontools.give` | Grant a unique instance bound to an allowed world |
| `/pt gui` | `plexontools.gui` | Open the administrative dashboard |
| `/pt reload` | `plexontools.reload` | Reload settings, messages, categories, tools, and world menus |

Aliases: `/plexontool` and `/plexontools`. `plexontools.admin` includes all administrative and bypass capabilities.

## Tracking and requirements

| Tracking type | Accepted activity | Target kind |
|---|---|---|
| `BLOCKS_BROKEN` | Successful block breaks | Bukkit `Material` blocks |
| `MOBS_KILLED` | Living entities killed by the holder | Bukkit `EntityType` |
| `ITEMS_FARMED` | Mature crops broken or harvested | Supported crop materials |
| `FISH_CAUGHT` | Cod, salmon, tropical fish, or pufferfish reeled in | Fish item materials |
| `DAMAGE_DEALT` | Final damage dealt to living entities | Bukkit `EntityType` |
| `BLOCKS_PLACED` | Successful block placements | Bukkit `Material` blocks |

A level's requirement is the activity needed to advance from that level to the next. GENERAL mode uses one counter:

```yaml
tracking:
  type: DAMAGE_DEALT
  mode: GENERAL
  amount: 1000
```

SPECIFIC mode requires every quota:

```yaml
tracking:
  type: BLOCKS_BROKEN
  mode: SPECIFIC
  targets:
    STONE: 500
    DEEPSLATE: 250
```

Levels can override root requirements with `requirement_mode`, `requirement`, or `requirements`.

## World menus, categories, and abilities

`menus.yml` reserves tools independently for each world. A reservation controls whether the player can activate that tool from `/pt`; the tool must also allow that world in `tools.yml`. Menu title, size, filler, tool membership, and slots are editable in-game. Explicit `/pt give` grants remain active administrator-issued instances until a player manages them through a matching world-menu reservation.

Every tool still has a `category` that resolves against `categories.yml`. In 3.5, categories organize definitions and retain explicit legacy showcase routes; they no longer control the default `/pt` player flow.

Abilities are complete per-level states. The legacy list form and the configurable map form are both accepted:

```yaml
levels:
  2:
    abilities:
      AUTO_SMELT:
        enabled: true
      AREA_MINE_3X3:
        enabled: true
      EXP_BOOSTER:
        multiplier: 1.75
      MOB_POTION_EFFECT:
        effect: minecraft:haste
        level: 2
        duration_ticks: 100
        target: HOLDER
      MAGNET:
        enabled: true
```

The 3×3 ability operates only on pickaxe, shovel, and axe material families. It checks synthetic block-break events before removing adjacent blocks so protection plugins can cancel them.

## Lore placeholders

Both `{placeholder}` and `<placeholder>` forms are accepted.

- Identity: `tool`, `tool_id`, `level_name`, `uuid`, `category`, `category_name`
- Progress: `level`, `max_level`, `next_level`, `current`, `required`, `remaining`, `percentage`, `total`, `progress_bar`
- Requirement: `requirement_mode`, `goal_type_description`, `target_progress`, `tracking`, `targets`
- Requirement rows: `requirement_action`, `requirement_target`, `requirement_goal`, `requirement_current`, `requirement_required`, `requirement_remaining`, `requirement_percentage`
- Binding: `bound_world`, `owner_name`, `owner_uuid`
- Profile: `material`, `enchantments`

The structured default layout lives under `default_lore_format` in `config.yml`. The special `{requirement_lines}` row expands to one line per SPECIFIC target and one summarized line for GENERAL requirements.

## Persistence

While materialized, the item carries `id`, `uuid`, `level`, `stat_count`, `category`, `bound_world`, `owner`, and optional `stat_breakdown` keys in the `plexontools` namespace. `data.yml` now also stores the authoritative activation entitlement so a deactivated or temporarily removed item can be reconstructed exactly. Snapshots are written asynchronously and atomically replaced where supported.

Block-break tracking remains material-based: matching player-placed blocks also count because PlexonTools does not maintain block-origin history.

## Documentation

- [Capabilities and configuration](docs/CAPABILITIES.md)
- [Administrative GUI](docs/ADMIN_EDITOR.md)
- [Architecture and persistence](docs/ARCHITECTURE.md)
- [Migrating from 2.0](docs/MIGRATION_3.md)
- [Migrating from 3.0 to 3.5](docs/MIGRATION_3_5.md)

## License

PlexonTools is available under the [MIT License](LICENSE).
