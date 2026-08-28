# PlexonTools

Progressive, world-bound custom tools for Paper servers in the Plexon ecosystem.

PlexonTools lets administrators create item-based tools that change as players use them. Every granted item receives a unique identity, owner, world binding, level, and progress counter through Paper's Persistent Data Container API.

> **Current release:** `1.0.0-beta.2` — **Creator:** Tonim (`ZpkDxGames`)

## Requirements

- Paper `1.21.4`
- Java `21`
- No runtime dependencies

## Highlights

- Block-break and mob-kill progression with optional target filters
- Complete per-level profiles: MiniMessage name, material, enchantments, lore, item flags, glint, and custom model data
- Visual enchantment controls and line-by-line lore editing in the admin GUI
- Level duplication, reordering, deletion, automatic renumbering, cumulative thresholds, and exact previews
- Material and display-name inheritance, so upgrades only need to declare the levels where they change
- Strict owner and bound-world checks with configurable bypass permissions
- Player showcase GUI with live owned-tool stats and next-level rewards
- Unique per-item UUIDs plus a cached `data.yml` instance registry
- Overflow-safe multi-level advancement
- Transactional GUI writes and immutable configuration caches on progression event paths
- Automatic profile synchronization for existing beta.1 items when they next gain progress

## Installation

1. Download `PlexonTools-1.0.0-beta.2.jar` from the GitHub release.
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

## Level profiles

A new instance begins at level `1`. A level's `requirement` is the progress needed to advance **from that level to the next**. The requirement is subtracted on advancement and overflow carries forward. At the final level, progress continues to be recorded but no additional upgrade occurs.

Each level is a complete item profile. Enchantments and lore belong specifically to that level. `display_name` and `material` are optional overrides: when omitted, the most recent earlier value is inherited, falling back to the tool's root `display_name` and `base_material`.

```yaml
tools:
  magma_breaker:
    enabled: true
    display_name: "<gold><bold>Magma Breaker</bold></gold>"
    base_material: IRON_PICKAXE
    allowed_worlds: [world, world_nether]
    tracking:
      type: BLOCKS_BROKEN
      targets: [STONE, COBBLESTONE, DEEPSLATE]
    levels:
      1:
        requirement: 500
        enchantments: {EFFICIENCY: 1, UNBREAKING: 1}
        item:
          unbreakable: false
          glint: AUTO
          hide_enchantments: false
          hide_attributes: false
        lore:
          - "<gray>{current}/{required} ({percent}%)</gray>"
          - "{bar}"
      2:
        requirement: 1500
        display_name: "<aqua><bold>Emberforged Breaker</bold></aqua>"
        material: DIAMOND_PICKAXE
        enchantments: {EFFICIENCY: 3, FORTUNE: 1}
        item:
          glint: ON
          custom_model_data: 1002
        lore:
          - "<gray>Remaining: {remaining}</gray>"
          - "{bar}"
```

Accepted `item.glint` values are `AUTO`, `ON`, and `OFF`. Enchantment levels may be configured from 1 through 255. The editor validates changes before saving them.

### Placeholders

- Identity: `{tool}`, `{tool_id}`, `{level_name}`, `{uuid}`
- Progress: `{level}`, `{max_level}`, `{next_level}`, `{current}`, `{required}`, `{remaining}`, `{percent}`, `{total}`, `{bar}`
- Binding: `{world}`, `{owner}`
- Profile: `{tracking}`, `{targets}`, `{material}`, `{enchantments}`

`{required}` and `{next_level}` render as `MAX` at the final level.

## Upgrading from beta.1

Replace the JAR and restart the server. Existing `tools.yml`, item PDC, and `data.yml` remain valid. The legacy `material_upgrade` key is still read and can be migrated naturally by editing that level in the GUI. Existing physical items receive the current level profile the next time they gain progress.

## Tracking note

Block tracking is currently material-based. Matching player-placed blocks count as progress; PlexonTools does not yet maintain block-origin history.

## World and ownership isolation

`allowed_worlds` controls where a definition may be used. When `/pt give` creates an instance, it binds the item to the recipient's current world. With default settings, the definition allowlist, immutable bound world, and owner must all match before the tool can be used or gain progress.

## License

PlexonTools is available under the [MIT License](LICENSE).
