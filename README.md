# PlexonTools

Progressive, world-bound custom tools for Paper servers in the Plexon ecosystem.

PlexonTools lets administrators define pickaxes, swords, axes, shovels, and other item-based tools that grow as players use them. Every granted item receives a unique identity, owner, world binding, progression level, and stat counter through Paper's Persistent Data Container API.

> **Current release:** `1.0.0-beta.1`
> **Creator:** Tonim (`ZpkDxGames`)

## Requirements

- Paper `1.21.4`
- Java `21`
- No required runtime dependencies

## Highlights

- Block-break and mob-kill progression with optional target filters
- Multiple levels with independent requirements, enchantments, lore, and material upgrades
- Strict owner and bound-world checks with configurable bypass permissions
- MiniMessage names, lore, messages, gradients, and progress bars
- Player showcase GUI with live stats from owned inventory tools
- In-game admin editor for tools, worlds, targets, thresholds, rewards, and lore
- Unique per-item UUIDs plus a cached `data.yml` instance registry
- Overflow-safe multi-level advancement (one event can cross multiple thresholds)
- Configuration caches on the event hot path; no disk writes per block or kill

## Installation

1. Build the project with Java 21:

   ```bash
   gradle clean build
   ```

2. Copy `build/libs/PlexonTools-1.0.0-beta.1.jar` into the server's `plugins` directory.
3. Start Paper once to generate `config.yml`, `messages.yml`, `tools.yml`, and `data.yml`.
4. Edit `tools.yml` manually or use `/pt gui`, then run `/pt reload`.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/pt` | `plexontools.use` | Open the player tool showcase |
| `/pt give <player> <tool_id>` | `plexontools.give` | Create and grant a uniquely identified tool |
| `/pt gui` | `plexontools.gui` | Open the administrative editor |
| `/pt reload` | `plexontools.reload` | Reload settings, messages, and tool definitions |

Aliases: `/plexontool` and `/plexontools`.

`plexontools.admin` grants every administrative permission. The narrower bypass permissions are `plexontools.bypass.world` and `plexontools.bypass.owner`.

## Progression semantics

A new instance begins at level `1`. The `requirement` configured on a level is the progress needed to advance **from that level to the next one**. When a tool advances, that requirement is subtracted and overflow carries forward. At the final configured level, progress is still recorded but no further upgrade occurs.

Each level describes the complete enchantment state for that level. On an upgrade, old enchantments are replaced by the new level's table. `material_upgrade` is optional; when omitted, the current material is retained.

Available lore placeholders:

- `{tool}` and `{tool_id}`
- `{uuid}`
- `{level}` and `{max_level}`
- `{current}` and `{required}` (`MAX` at the final level)
- `{world}` and `{owner}`
- `{bar}`

## World and ownership isolation

`allowed_worlds` controls where a definition may exist. When `/pt give` creates an instance, it binds that item to the recipient's current world. With the default settings, both conditions must pass before the item can break blocks, attack, interact, take durability damage, or gain progress:

1. The current world is in the definition's allowlist.
2. The current world matches the instance's immutable `plexontools:bound_world` value.

The instance owner is stored in PDC and the registry. Other players cannot use it unless they have the owner bypass permission.

## Example

```yaml
tools:
  magma_breaker:
    enabled: true
    display_name: "<gradient:#FF4500:#FFA500><bold>Magma Breaker</bold></gradient>"
    base_material: NETHERITE_PICKAXE
    allowed_worlds: [world, world_nether]
    tracking:
      type: BLOCKS_BROKEN
      targets: [STONE, COBBLESTONE, DEEPSLATE]
    levels:
      1:
        requirement: 500
        enchantments:
          EFFICIENCY: 1
          UNBREAKING: 1
        lore:
          - "<gray>Progress: <orange>{current}/{required}</orange></gray>"
          - "{bar}"
      2:
        requirement: 1500
        enchantments:
          EFFICIENCY: 2
          UNBREAKING: 2
          FORTUNE: 1
        material_upgrade: NETHERITE_PICKAXE
        lore:
          - "<gold><bold>Upgraded tier!</bold></gold>"
          - "<gray>Level {level}/{max_level}</gray>"
```

An empty `tracking.targets` list means every block or living entity for that tracking type.

## License

PlexonTools is available under the [MIT License](LICENSE).
