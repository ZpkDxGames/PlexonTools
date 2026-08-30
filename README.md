# PlexonTools

PlexonTools is a Paper-native progression engine for unique, world-activated custom tools. Every tool has its own UUID, permanent owner, world binding, activation state, level, aggregate progress, and optional per-target counters.

> **Current release:** `3.6.1` — **Creator:** Tonim (`ZpkDxGames`)

## Requirements

- Paper `1.21.4`
- Java `21`
- No external database service or manually installed runtime dependency
- No NMS or CraftBukkit implementation access

## 3.6.1 highlights

- `/pt` is now a configurable per-world activation menu instead of a category browser.
- A tool appears automatically when its `allowed_worlds` includes the current world; `menus.yml` pins exact slots instead of acting as a second hidden allowlist.
- Admins customize each world's title, rows, filler, pinned slots, default tool cards, and ON/OFF panels through `/pt gui` or YAML.
- Players can activate and deactivate an available tool without losing its UUID, level, or progress.
- A tool can share one player-owned progression record across Overworld, Nether, and End variants, or keep intentional per-world progression.
- Bound tools are always unbreakable, owner-only, non-droppable, retained on death, and blocked from external inventories.
- SPECIFIC objectives render one requirement per lore line; enchantments, attributes, unbreakable text, and additional vanilla details are hidden.
- Six tracking types: blocks broken, mobs killed, items farmed, fish caught, damage dealt, and blocks placed.
- GENERAL shared totals and SPECIFIC per-target quotas reset at each level boundary; excess activity never counts toward the next level.
- A configurable action bar shows current progress; item lore/PDC and action-bar rendering coalesce per instance while authoritative progress updates immediately.
- PlexonTools claims its GUI clicks, uses distinct navigation items, and rejects external inventory opens throughout its GUI session so unrelated plugins cannot hijack Back or page navigation.
- Mutable player/tool state now lives in generated `plexontools.db` SQLite storage with WAL, integrity checks, indexes, prepared statements, and transactional batches.
- Normal gameplay performs no YAML or database I/O: repeated UUID updates coalesce in memory and flush asynchronously in bounded batches.
- Existing schema-v3/v4 `data.yml` registries migrate automatically and idempotently with a timestamped backup and post-import verification.
- `/pt backup` creates a checkpointed SQLite backup under `plugins/PlexonTools/backups`.
- Every administrator YAML now includes an inline schema guide; the global `tool-lore.template` list is freely reorderable and GENERAL, SPECIFIC, and MAX rows have separate formats.
- Current documented copies of every editable YAML are refreshed under `plugins/PlexonTools/examples` without overwriting live configuration.
- Per-level Auto Smelt, protected-aware 3×3 mining, EXP Booster, potion effect, and Magnet abilities.
- An in-game dashboard for world menus, tools, internal categories, global settings, requirements, levels, and abilities.
- `<!italic>` normalization for every MiniMessage deserialization, including names, lore, messages, and GUIs.
- Item PDC mutations and progression calculations stay in memory; changed registry records persist asynchronously in coalesced SQLite transactions.
- Backward-compatible loading for 2.0 definitions, issued items, list filters, and legacy `data.yml` records.
- Bundled defaults provide a gold-themed, 100-level `legendary_pickaxe` shared across `Survival_World` and its Nether/End variants.

## Installation

1. Download `PlexonTools-3.6.1.jar` from the GitHub release.
2. Place it in the Paper server's `plugins` directory.
3. Start the server once to generate the five editable YAML files, their `examples/` references, and `plexontools.db`.
4. Customize through `/pt gui` or YAML, then run `/pt reload`.

Build from source with Java 21 and `gradle clean build`.

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
- Progress: `level`, `max_level`, `next_level`, `current`, `required`, `remaining`, `percentage`, `total`, `progress_bar`
- Requirement: `requirement_mode`, `goal_type_description`, `target_progress`, `tracking`, `targets`
- Requirement rows: `requirement_action`, `requirement_target`, `requirement_goal`, `requirement_current`, `requirement_required`, `requirement_remaining`, `requirement_percentage`
- Binding: `bound_world`, `owner_name`, `owner_uuid`
- Profile: `material`, `enchantments`
- Player menu state: `world`, `status`, `state`, `state_symbol`, `toggle_action`, `toggle_hint`

The freely ordered default layout lives under `tool-lore.template` in `config.yml`. The special `{requirement_lines}` row expands to one line per SPECIFIC target and one summarized line for GENERAL requirements. `tool-lore.requirements` gives GENERAL, SPECIFIC, and maximum-level rows independent formats. A root or per-level `lore` list in `tools.yml` can override the global template; `lore: []` intentionally removes it.

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
