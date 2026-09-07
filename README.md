# PlexonTools

PlexonTools is a Paper-native progression engine for unique, world-activated custom tools. Every tool has its own UUID, permanent owner, world binding, activation state, level, aggregate progress, and optional per-target counters.

> **Current release:** `4.1.0` — **Creator:** Tonim (`ZpkDxGames`)

## Requirements

- Paper `26.2`
- Java `25`
- PlexonCore `1.0.0` is optional at runtime; PlexonTools remains fully functional in standalone mode
- No external database service or manually installed runtime dependency
- No NMS or CraftBukkit implementation access

## 4.1.0 highlights

- Preserves the recovered production 4.0.0 gameplay, UUID/PDC identity, 100-level definitions, natural/player-placed block provenance, abilities, GUI, and SQLite schema behavior.
- Registers as PlexonCore module `tools` against Core API `>=1.0 <2.0` when PlexonCore 1.0.0 is present and compatible.
- Falls back safely to `STANDALONE` mode when Core is absent, disabled, incompatible, or unavailable.
- Registers the read-only `com.plexon.tools.api.PlexonToolsAPI` through Bukkit `ServicesManager` in both Core and standalone modes.
- Emits post-commit `PlexonToolProgressEvent` and `PlexonToolLevelUpEvent` events for PlexonQuests 3.1.0 without a direct Quests dependency.
- Uses unique event IDs and one transaction ID per accepted progression mutation, while preserving 4.0.0's one-level-per-action/no-overflow progression behavior.
- Adds `/pt diagnostics` for Core mode/state, API/event availability, SQLite WAL state, definition counts, tracked instances, and pending persistence work.
- Builds for Paper 26.2 / Java 25 and verifies that PlexonCore runtime classes are not shaded into the plugin JAR.

The detailed integration contracts are documented in [docs/API.md](docs/API.md), [docs/PLEXONCORE.md](docs/PLEXONCORE.md), and [docs/MIGRATION_4_1.md](docs/MIGRATION_4_1.md).

## Installation

1. Download `PlexonTools-4.1.0.jar` from the GitHub release.
2. Place it in the Paper server's `plugins` directory.
3. Start the server once to generate the five editable YAML files, their `examples/` references, and `plexontools.db`.
4. Customize through `/pt gui` or YAML, then run `/pt reload`.

Build from source with Java 25. PlexonCore 1.0.0 is a compile-only dependency; CI provisions the pinned Core release JAR into Maven Local before `gradle clean build`.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/pt` | `plexontools.use` | Open the current world's tool activation menu |
| `/pt <category> [player]` | `plexontools.use`; target requires `plexontools.admin` | Open one category |
| `/pt all [player]` | `plexontools.use`; target requires `plexontools.admin` | Open the unified showcase |
| `/pt give <player> <tool_id> [world]` | `plexontools.give` | Grant a unique instance for an allowed world |
| `/pt gui` | `plexontools.gui` | Open the administrative dashboard |
| `/pt reload` | `plexontools.reload` | Reload settings, messages, categories, tools, and world menus |
| `/pt backup` | `plexontools.backup` | Flush pending records and create a consistent SQLite backup |
| `/pt diagnostics` | `plexontools.diagnostics` | Show Core/API/event and SQLite runtime health |

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

For SPECIFIC `BLOCKS_BROKEN` profiles, reload also checks the resolved material at every level. Vanilla pickaxes, axes, shovels, and hoes must match the block's mineable tag and required harvest tier; an invalid level is rejected while the last valid runtime configuration remains active.

Progress is strictly per level. When a level completes, both its aggregate counter and SPECIFIC target counters reset to zero. For example, two consecutive levels that each require `STONE: 500` require 500 new Stone breaks at each level. The event that completes one level cannot contribute overflow to the next.

Accepted requirement activity updates authoritative state immediately. The item lore/PDC and live action bar refresh together in a short configurable window (`performance.progress-visual-refresh-ticks`, default `4`) to reduce block-event work. Toggle the action bar with `effects.progress-action-bar` and customize `messages.progress-update`.

## Multi-dimension progression

Every tool can choose its persistence boundary in `tools.yml`:

```yaml
allowed_worlds:
  - Survival_World
  - Survival_World_nether
  - Survival_World_the_end
progression:
  scope: PLAYER
  anchor_world: Survival_World
```

`PLAYER` keeps one UUID, level, GENERAL counter, and SPECIFIC target map across every allowed world. The anchor must appear in `allowed_worlds`; it is the canonical record when older per-world copies already exist. `WORLD` deliberately keeps independent progress in each allowed world. Existing multi-world definitions with no explicit scope default to `PLAYER`; existing single-world definitions remain `WORLD` until configured or expanded. World names must match the exact Bukkit names used by the server (comparison is case-insensitive).

## World menus, categories, and abilities

An enabled tool appears in `/pt` by default whenever its `allowed_worlds` list contains the player's current world. `menus.yml` customizes the inventory title, size, filler, and exact pinned slots; allowed tools without a pin are placed automatically. Set `world-menu.auto-show-allowed-tools: false` to restore strict explicit membership, where only pinned tools appear. Explicit `/pt give` grants remain active administrator-issued instances until the player manages them through `/pt`.

The default player-facing card and the separate ON/OFF panel are configured under `world-menu` in `config.yml`, or in-game through `/pt gui` → **Player Menu Appearance**. When the slot directly below a card is free, the panel is placed there; otherwise the card itself remains the toggle control.

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
- Progress: `level`, `max_level`, `next_level`, `current`, `required`, `remaining`, `percentage`, `total`, `progress_bar`, `current_color`, `percentage_color`
- Requirement: `requirement_mode`, `goal_type_description`, `target_progress`, `tracking`, `targets`
- Requirement rows: `requirement_action`, `requirement_target`, `requirement_goal`, `requirement_current`, `requirement_required`, `requirement_remaining`, `requirement_percentage`, `requirement_current_color`
- Enchantment rows: `enchantment_key`, `enchantment_name`, `enchantment_level`, `enchantment_level_roman`
- Binding: `bound_world`, `owner_name`, `owner_uuid`
- Profile: `material`, `material_name`, `enchantments`, `enchantment_count`
- Player menu state: `world`, `status`, `state`, `state_symbol`, `toggle_action`, `toggle_hint`

The freely ordered compact default layout lives under `tool-lore.template` in `config.yml`. `{enchantment_lines}` expands the active profile into readable enchantment rows, while `{requirement_lines}` expands to one line per SPECIFIC target or one summarized GENERAL row. `progress-value-colors` drives the current value from red through amber to green; templates keep the required value fixed. A root or per-level `lore` list in `tools.yml` can override the global template; `lore: []` intentionally removes it.

## Persistence

While materialized, the item carries `id`, `uuid`, `level`, `stat_count`, `category`, `bound_world`, `owner`, and optional `stat_breakdown` keys in the `plexontools` namespace. `plexontools.db` stores players, authoritative activation entitlements, tool instances, and normalized target progress. Gameplay updates remain in memory; visual metadata is coalesced on the server thread, an asynchronous worker persists bounded database transactions, and shutdown drains the queue before checkpointing WAL.

On the first 3.6 startup, an existing schema-v3/v4 `data.yml` is strictly validated, backed up as `data.yml.pre-sqlite-<timestamp>.bak`, imported in one transaction, verified, and marked migrated. The original remains available for rollback and is never re-imported after a successful migration.

Block-break tracking remains material-based: matching player-placed blocks also count because PlexonTools does not maintain block-origin history.

## Documentation

- [PlexonTools 3.6.1 performance and multi-dimension guide](docs/PLEXONTOOLS_3_6_1.md)
- [PlexonTools 3.6.0 database and configuration guide](docs/PLEXONTOOLS_3_6_0.md)
- [PlexonTools 3.5.2 release behavior](docs/PLEXONTOOLS_3_5_2.md)
- [PlexonTools 3.5.1 baseline and 3.6 roadmap](docs/PLEXONTOOLS_3_5_1.md)
- [Capabilities and configuration](docs/CAPABILITIES.md)
- [Administrative GUI](docs/ADMIN_EDITOR.md)
- [Architecture and persistence](docs/ARCHITECTURE.md)
- [Migrating from 2.0](docs/MIGRATION_3.md)
- [Migrating from 3.0 to 3.5](docs/MIGRATION_3_5.md)
- [Migrating from 3.5 to 3.6](docs/MIGRATION_3_6.md)

## License

PlexonTools is available under the [MIT License](LICENSE).
