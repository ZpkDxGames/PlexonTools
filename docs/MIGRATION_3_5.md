# Migrating PlexonTools 3.0 to 3.5

## Before upgrading

1. Stop the Paper server.
2. Back up the complete `plugins/PlexonTools` directory.
3. Replace the 3.0 JAR with `PlexonTools-3.5.0.jar`.
4. Start the server. PlexonTools creates `menus.yml` without overwriting existing files.

## Configure `/pt`

The default `/pt` route no longer opens categories. It opens the current world's activation menu.

- Use `/pt gui` → **World Tool Menus** to configure loaded worlds.
- Reserve only tools whose `allowed_worlds` includes the target world.
- Customize the title, row count, filler, and exact slots.
- `menus.yml` ships examples for `world` and `world_nether`; rename or remove them for different world names.

Explicit `/pt <category>` and `/pt all` routes remain available for compatibility, but categories do not control the default player menu.

## Existing tools and data

No manual item conversion is required.

- Registry schema v3 entries load as `active: true`, `menu_managed: false` and are written as schema v4 at the next asynchronous checkpoint.
- Existing PDC preserves UUID, owner, bound world, level, aggregate progress, category, and SPECIFIC target counters.
- Join/reload reconciliation adopts issued items missing from `data.yml`.
- If duplicate instances exist for the same owner/tool/world, reconciliation keeps the newest active record and deactivates extras.
- Active tools outside their bound world are removed temporarily and restored when the owner returns.
- Existing and explicit `/pt give` instances remain administrator-managed until the player activates them through a matching world-menu reservation. Removing a reservation only revokes menu-managed instances.

## Permanent protections

Version 3.5 ignores legacy per-level `item.unbreakable`, `hide_enchantments`, and `hide_attributes` values at runtime. Every Plexon tool is unbreakable and hides enchantments, stored enchantments, attributes, the unbreakable line, and additional vanilla item details.

The `settings.enforce-owner` key remains readable so old configurations load, but owner binding is always enforced and `plexontools.bypass.owner` has been removed.

## Lore migration

Existing `config.yml` files do not need to be replaced. When `default_lore_format.stats.objective_header` or `requirement_line` is absent, 3.5 supplies clean defaults in memory.

For custom lore, add this standalone entry where the objectives should appear:

```yaml
- "{requirement_lines}"
```

Customize the generated rows in `config.yml`:

```yaml
default_lore_format:
  stats:
    objective_header: "<gray>Objectives:</gray>"
    requirement_line: "<dark_gray> •</dark_gray> <white>{requirement_goal}</white> <dark_gray>—</dark_gray> <aqua>{requirement_current}</aqua><dark_gray>/</dark_gray><green>{requirement_required}</green>"
```

Custom legacy lines containing `{goal_type_description}` are also compatible: SPECIFIC requirements automatically repeat that line once per target.

## Full inventories

PlexonTools never drops a protected tool to make space. A new activation fails cleanly when no slot is available; an already-active entitlement remains stored and the player receives a warning if automatic restoration cannot materialize it. Free one inventory slot and reopen `/pt`, change worlds, relog, or reload the plugin to reconcile it.
