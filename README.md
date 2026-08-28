# PlexonTools

Progressive, world-bound custom tools for Paper servers in the Plexon ecosystem.

PlexonTools lets administrators build tools that evolve as players use them. Every granted item has a unique identity, owner, world binding, current level, and item-local progression state backed by Paper's Persistent Data Container API.

> **Current release:** `2.0.0` — **Creator:** Tonim (`ZpkDxGames`)

## Requirements

- Paper `1.21.4`
- Java `21`
- No runtime dependencies

## 2.0 highlights

- GENERAL requirements count one shared total across all blocks or living mobs.
- SPECIFIC requirements maintain independent, persistent quotas for every selected material or entity.
- Per-level requirement modes, targets, amounts, display names, materials, enchantments, lore, glint, item flags, and custom model data.
- Searchable target browser plus `+1`, `+10`, `+100`, `+1000`, matching decrement controls, and exact chat entry.
- Visual enchantment controls and line-by-line lore editing.
- Level duplication, reordering, deletion, automatic renumbering, resolved previews, and overflow-safe advancement.
- Strict owner and world checks with action-bar warnings and narrow bypass permissions.
- Standard MiniMessage lore with dynamic objective, aggregate or per-target progress, owner, world, and progress-bar values.
- Transactional configuration writes and a cached immutable runtime registry; no disk I/O occurs per progression event.
- In-place compatibility with beta.1 and beta.2 configurations and issued items.

## Installation

1. Download `PlexonTools-2.0.0.jar` from the GitHub release.
2. Copy it into the server's `plugins` directory.
3. Start Paper once to generate `config.yml`, `messages.yml`, `tools.yml`, and `data.yml`.
4. Use `/pt gui` or edit `tools.yml`, then run `/pt reload`.

To build from source, run `gradle clean build` with Java 21.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/pt` | `plexontools.use` | Open the player tool showcase |
| `/pt give <player> <tool_id>` | `plexontools.give` | Grant a uniquely identified tool |
| `/pt gui` | `plexontools.gui` | Open the administrative editor |
| `/pt reload` | `plexontools.reload` | Reload settings, messages, and definitions |

Aliases: `/plexontool` and `/plexontools`.

`plexontools.admin` grants every administrative permission. The narrower bypass permissions are `plexontools.bypass.world` and `plexontools.bypass.owner`.

## Requirement engine

A level's requirement is the work needed to advance **from that level to the next**. Overflow carries forward when the next level accepts it. At the final level, matching activity continues to be recorded without another upgrade.

GENERAL mode counts any event of the configured tracking type:

```yaml
tracking:
  type: BLOCKS_BROKEN
  mode: GENERAL
  amount: 1000
```

SPECIFIC mode requires every configured quota:

```yaml
tracking:
  type: BLOCKS_BROKEN
  mode: SPECIFIC
  targets:
    STONE: 500
    DEEPSLATE: 200
```

Levels inherit the root mode and values unless they declare `requirement_mode`, `requirement`, or `requirements`:

```yaml
tools:
  magma_breaker:
    enabled: true
    display_name: "<gradient:#FF4500:#FFA500><bold>Magma Breaker</bold></gradient>"
    base_material: IRON_PICKAXE
    allowed_worlds: [world, world_nether]
    tracking:
      type: BLOCKS_BROKEN
      mode: SPECIFIC
      targets:
        STONE: 500
        COBBLESTONE: 250
    levels:
      1:
        requirement_mode: SPECIFIC
        enchantments: {EFFICIENCY: 1, UNBREAKING: 1}
      2:
        requirement_mode: SPECIFIC
        requirements: {STONE: 1000, COBBLESTONE: 500}
        display_name: "<gradient:#00DBDE:#FC00FF><bold>Emberforged Breaker</bold></gradient>"
        material: DIAMOND_PICKAXE
        enchantments: {EFFICIENCY: 3, FORTUNE: 1}
        item:
          glint: ON
          custom_model_data: 1002
```

`display_name` and `material` are inherited from the most recent earlier profile. `material_upgrade` remains accepted as a legacy alias. Enchantments, lore, and item options are complete per-level states.

## Lore placeholders

Both `{placeholder}` and `<placeholder>` forms are accepted.

- Identity: `tool`, `tool_id`, `level_name`, `uuid`
- Progress: `level`, `max_level`, `next_level`, `current`, `current_xp`, `required`, `required_xp`, `remaining`, `percent`, `percentage`, `total`, `bar`, `progress_bar`
- Requirement: `requirement_mode`, `goal_type_description`, `target_progress`, `tracking`, `targets`
- Binding: `world`, `bound_world`, `owner`, `owner_name`, `owner_uuid`
- Profile: `material`, `enchantments`

`required` and `next_level` render as `MAX` at the final level. The default layout lives under `default-lore-format.lines` in `config.yml`; any level can override it with its own `lore` list.

## Upgrading from beta releases

Back up the plugin directory, replace the JAR, and restart Paper. Existing `tools.yml`, `data.yml`, and mandatory item PDC keys remain valid.

- A beta `tracking.targets` list keeps its original semantics: any listed target contributes to one shared total.
- A 2.0 `tracking.targets` map defines independent SPECIFIC quotas.
- Existing items without `stat_breakdown` load with an empty breakdown and continue normally.
- Existing items receive the current resolved level profile when their progress next changes.

The plugin does not rewrite an existing bundled configuration on startup.

## Tracking note

Block tracking is material-based. Matching player-placed blocks count as progress; PlexonTools does not maintain block-origin history in 2.0.

## World and ownership isolation

`allowed_worlds` controls where a definition may progress. `/pt give` also binds each physical instance to the recipient's current world. With default settings, the allowlist, immutable bound world, and owner must all match. Unauthorized attempts are blocked from progression and report the configured action-bar warning.

## License

PlexonTools is available under the [MIT License](LICENSE).
