# PlexonTools

PlexonTools is a Paper-native progression engine for unique, category-driven custom tools. Every granted item has its own UUID, owner, world binding, level, aggregate progress, and optional per-target counters stored directly in Persistent Data Container metadata.

> **Current release:** `3.0.0` — **Creator:** Tonim (`ZpkDxGames`)

## Requirements

- Paper `1.21.4`
- Java `21`
- No runtime dependencies
- No NMS or CraftBukkit implementation access

## 3.0 highlights

- Six tracking types: blocks broken, mobs killed, items farmed, fish caught, damage dealt, and blocks placed.
- GENERAL shared totals and SPECIFIC per-target quotas on every level, with compatible overflow carried forward.
- Category selection and per-category player showcases configured through `categories.yml`.
- Per-level Auto Smelt, protected-aware 3×3 mining, EXP Booster, potion effect, and Magnet abilities.
- An in-game dashboard for tools, categories, global settings, requirements, targets, levels, item profiles, and abilities.
- `<!italic>` normalization for every MiniMessage deserialization, including names, lore, messages, and GUIs.
- Item PDC mutations and progression calculations stay in memory; the audit registry checkpoints asynchronously.
- Backward-compatible loading for 2.0 definitions, issued items, list filters, and `data.yml` records.

## Installation

1. Download `PlexonTools-3.0.0.jar` from the GitHub release.
2. Place it in the Paper server's `plugins` directory.
3. Start the server once to generate `config.yml`, `messages.yml`, `categories.yml`, `tools.yml`, and `data.yml`.
4. Customize through `/pt gui` or YAML, then run `/pt reload`.

Build from source with Java 21 and `gradle clean build`.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/pt` | `plexontools.use` | Open categories, or the showcase when one category exists |
| `/pt <category> [player]` | `plexontools.use`; target requires `plexontools.admin` | Open one category |
| `/pt all [player]` | `plexontools.use`; target requires `plexontools.admin` | Open the unified showcase |
| `/pt give <player> <tool_id> [world]` | `plexontools.give` | Grant a unique instance bound to an allowed world |
| `/pt gui` | `plexontools.gui` | Open the administrative dashboard |
| `/pt reload` | `plexontools.reload` | Reload settings, messages, categories, and tools |

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

## Categories and abilities

Every tool has a `category` that resolves against `categories.yml`. Categories control their MiniMessage name, icon, showcase slot, and description.

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
- Binding: `bound_world`, `owner_name`, `owner_uuid`
- Profile: `material`, `enchantments`

The structured default layout lives under `default_lore_format` in `config.yml`.

## Persistence

The item is the progression source of truth. It carries `id`, `uuid`, `level`, `stat_count`, `category`, `bound_world`, `owner`, and optional `stat_breakdown` keys in the `plexontools` namespace. `data.yml` is an audit snapshot written periodically from an immutable in-memory copy and atomically replaced where the filesystem supports it.

Block-break tracking remains material-based: matching player-placed blocks also count because PlexonTools does not maintain block-origin history.

## Documentation

- [Capabilities and configuration](docs/CAPABILITIES.md)
- [Administrative GUI](docs/ADMIN_EDITOR.md)
- [Architecture and persistence](docs/ARCHITECTURE.md)
- [Migrating from 2.0](docs/MIGRATION_3.md)

## License

PlexonTools is available under the [MIT License](LICENSE).
