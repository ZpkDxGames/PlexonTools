# PlexonTools

PlexonTools is a Paper-native progression engine for unique, world-activated custom tools. Every tool has its own UUID, permanent owner, world binding, activation state, level, aggregate progress, and optional per-target counters.

> **Current stable:** `4.2.1` — **Phase 2 candidate:** `4.3.0-rc.2` — **Creator:** Tonim (`ZpkDxGames`)

## Requirements

- Paper `26.2`
- Java `25`
- PlexonCore `2.0.4` is the certified ecosystem API target for the Phase 2 candidate; Legendary Tools gameplay and provenance remain local to PlexonTools
- No external database service or manually installed runtime dependency
- No NMS or CraftBukkit implementation access

## 4.3.0-rc.2 Phase 2 highlights

- Keeps the optimized ordinary single-block Legendary Tool path authoritative in memory with no scheduler, database write, YAML traversal, or full item rebuild per accepted normal block.
- Keeps PlexonTools as the sole Legendary Tools gameplay/provenance authority for this RC while integrating PlexonCore 2.0.4 for lifecycle/module status.
- Makes `UNKNOWN` block provenance unconditionally fail closed for natural-only progression and exposes aggregate origin/load diagnostics.
- Hard-bounds the latest-state acceleration cache; the existing registry remains authoritative after eviction.
- Preserves coalesced visual refreshes, coalesced public progress notifications, bounded dirty persistence and exact progression totals.
- Removes PlexonTools-owned recursive secondary `BlockBreakEvent` fan-out. Area Mine secondary breaking is `DISABLED_SAFE` in this RC until a direct protection/provenance-equivalent path can be certified.
- Adds all-file reload preflight/fingerprinting and regression contracts for known single-block performance anti-patterns.
- Preserves the v4.2.1 physical PDC identity, SQLite data, API/events and premium visual language.

The detailed Phase 2 architecture, migration and runtime gates are documented in [docs/PHASE2_4_3_0_RC2.md](docs/PHASE2_4_3_0_RC2.md). This RC is not a stable-production promotion; rollback remains `v4.2.1`.

## Installation

For production, continue using `PlexonTools-4.2.1.jar` until the Phase 2 runtime gate is completed. To runtime-test the prerelease:

1. Back up the existing `plugins/PlexonTools/` directory and database.
2. Download `PlexonTools-4.3.0-rc.2.jar` from the GitHub prerelease.
3. Replace only the plugin JAR; keep the existing PlexonTools data/configuration files.
4. Start Paper 26.2 with Java 25 and PlexonCore 2.0.4.
5. Verify `/pt diagnostics` before representative mining tests.

Build from source with Java 25. PlexonCore 2.0.4 is a compile-only dependency; CI provisions the pinned Core release JAR before `gradle clean check build`.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/pt` | `plexontools.use` | Open the current world's tool activation menu |
| `/pt <category> [player]` | `plexontools.use`; target requires `plexontools.admin` | Open one category |
| `/pt all [player]` | `plexontools.use`; target requires `plexontools.admin` | Open the unified showcase |
| `/pt give <player> <tool_id> [world]` | `plexontools.give` | Grant a unique instance for an allowed world |
| `/pt gui` | `plexontools.gui` | Open the administrative dashboard |
| `/pt reload` | `plexontools.reload` | Preflight and reload settings, messages, categories, tools, and world menus |
| `/pt backup` | `plexontools.backup` | Flush pending records and create a consistent SQLite backup |
| `/pt diagnostics` | `plexontools.diagnostics` | Show authority, provenance, Core/API/event, reload and SQLite runtime health |

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

For SPECIFIC `BLOCKS_BROKEN` profiles, reload checks the resolved material at every level. Vanilla pickaxes, axes, shovels, and hoes must match the block's mineable tag and required harvest tier; invalid candidate definitions are rejected by the Phase 2 preflight before the live runtime is mutated.

Progress is strictly per level. When a level completes, both its aggregate counter and SPECIFIC target counters reset to zero. The event that completes one level cannot contribute overflow to the next unless the configured progression contract explicitly supports a larger delta path.

Accepted requirement activity updates authoritative state immediately. The item lore/PDC and live action bar refresh together in a short configurable window (`performance.progress-visual-refresh-ticks`, default `4`) to reduce event work. Compatible public progress notifications are also coalesced briefly; consumers must use `PlexonToolProgressEvent.amount()` because one event may represent multiple accepted units. Level-up events remain immediate.

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

The default player-facing card and the separate ON/OFF panel are configured under `world-menu` in `config.yml`, or in-game through `/pt gui` → **Player Menu Appearance**. The Phase 2 candidate intentionally does not redesign these visuals.

Every tool still has a `category` that resolves against `categories.yml`. Abilities remain complete per-level states, and the legacy list form plus configurable map form remain accepted.

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

**Phase 2 Area Mine safety gate:** `AREA_MINE_3X3` definitions remain load-compatible, but secondary Area Mine execution is `DISABLED_SAFE` in `4.3.0-rc.2`. PlexonTools no longer synthesizes recursive `BlockBreakEvent`s for adjacent blocks, and it will not directly mutate those blocks until protection/provenance equivalence is implemented and certified. This gate is checked before neighbor-plane allocation, so it does not add secondary block work to normal mining.

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

The freely ordered compact default layout lives under `tool-lore.template` in `config.yml`. `{enchantment_lines}` expands the active profile into readable enchantment rows, while `{requirement_lines}` expands to one line per SPECIFIC target or one summarized GENERAL row. The Phase 2 candidate preserves the established visual language and only changes rendering coordination where required for performance/correctness.

## Persistence and provenance

While materialized, the item carries `id`, `uuid`, `level`, `stat_count`, `category`, `bound_world`, `owner`, and optional `stat_breakdown` keys in the `plexontools` namespace. `plexontools.db` stores players, authoritative activation entitlements, tool instances, normalized target progress, and natural/player-placed block provenance. Gameplay updates remain in memory; visual metadata is coalesced on the server thread, an asynchronous worker persists bounded database transactions, and shutdown drains the queue before checkpointing WAL.

Player-placed blocks are indexed and excluded from natural-only block/farming progression. Provenance checks stay in memory on the gameplay thread; persistence and chunk warming are asynchronous. Phase 2 treats `UNKNOWN` conservatively: it is rejected for natural-only progression regardless of the historical fail-open configuration flag. `DISABLED` remains a distinct policy state when natural-block filtering itself is intentionally disabled.

## Documentation

- [PlexonTools 4.3.0-rc.2 Phase 2 certification record](docs/PHASE2_4_3_0_RC2.md)
- [PlexonTools 4.1.1 release notes](RELEASE_NOTES.md)
- [PlexonTools 3.6.1 performance and multi-dimension guide](docs/PLEXONTOOLS_3_6_1.md)
- [PlexonTools 3.6.0 database and configuration guide](docs/PLEXONTOOLS_3_6_0.md)
- [Capabilities and configuration](docs/CAPABILITIES.md)
- [Administrative GUI](docs/ADMIN_EDITOR.md)
- [Architecture and persistence](docs/ARCHITECTURE.md)
- [Public API](docs/API.md)
- [PlexonCore integration](docs/PLEXONCORE.md)

## License

PlexonTools is available under the [MIT License](LICENSE).
