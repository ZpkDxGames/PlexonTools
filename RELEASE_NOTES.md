# PlexonTools 3.5.0

PlexonTools 3.5 changes custom tools from permanently carried items into persistent, world-scoped player entitlements. Players open `/pt` and decide which administrator-reserved tools are active in their current world; deactivating a tool stores it without losing any progress.

## World activation menus

- Every world can have an independent title, row count, filler material/name, tool selection, and slot arrangement.
- Administrators configure the menus in `/pt gui` or `menus.yml`.
- A tool must be reserved in the world menu and include that world in its `allowed_worlds` list.
- Active tools are removed when the owner leaves their bound world and restored when they return.
- Join, respawn, reload, and full-inventory reconciliation use the persistent registry rather than creating new instances.

## Permanent tool protection

- Every custom tool is unbreakable regardless of legacy level configuration.
- The owner cannot drop it with Q, Ctrl+Q, death, or container interaction.
- Death retention uses Paper's keep-item collection while removing the item from normal drops to prevent duplication.
- Non-owners cannot pick up or use a bound tool, including through the old owner-bypass permission.
- Deactivation and full-inventory handling never spawn an item entity.

## Clean lore and tooltips

- `{requirement_lines}` expands GENERAL requirements into one summary row and SPECIFIC requirements into one row per target.
- Each row exposes goal, target, current, required, remaining, and percentage placeholders.
- Legacy `{goal_type_description}` lore lines are repeated per SPECIFIC target automatically.
- Enchantment text, stored enchantments, attributes, the unbreakable label, and additional item-specific tooltip data are hidden while enchantments and glint remain functional.
- Cached offline player names are preferred over raw owner UUIDs when available.

## Compatibility

The v4 registry reads v3 `data.yml` records as active by default. Existing items retain their UUID, level, aggregate progress, target counters, category, owner, and bound world. Existing `config.yml` files do not need the new lore keys: clean objective and requirement-row defaults are supplied by the plugin.

Paper 1.21.4 and Java 21 remain required. No NMS, CraftBukkit implementation access, or runtime dependency was added.
