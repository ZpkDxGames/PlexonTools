# PlexonTools 3.5 admin editor

Open `/pt gui` with `plexontools.gui`.

## Dashboard

- **Tool Manager** opens every definition and its live preview.
- **Create New Tool** uses the held material, current world, first category, GENERAL block tracking, and a safe default profile.
- **World Tool Menus** configures the player-facing `/pt` layout for every loaded or manually named world.
- **Player Menu Appearance** edits the default tool card and ON/OFF panel used in `/pt`.
- **Category Manager** retains internal tool organization and legacy showcase metadata.
- **Global Settings** controls bound-world enforcement and level-up effects. Permanent owner binding is displayed but cannot be disabled.
- **Live World Menu** previews the current world's activation GUI.

## World menu editor

Select a loaded world or use **Add Unloaded World** to enter an exact name. Each world controls:

- MiniMessage inventory title, including the `{world}` placeholder.
- Three to six inventory rows.
- Filler item material and MiniMessage display name.
- Optional exact slot pins for selected tools.

An enabled tool whose `allowed_worlds` includes this world appears automatically by default. Left-click a tool to pin or unpin its exact layout slot and right-click a pinned tool to enter a slot. Set **Auto-show Allowed Tools** off in **Player Menu Appearance** only when you want pins to act as strict menu membership. Shrinking a menu is rejected if an existing pin would fall outside the new content area.

## Player menu appearance

The appearance editor controls the default card material, display name, lore, active glint, and the active/inactive material, name, and lore for the ON/OFF panel. The card material may follow the player's saved tool level (`TOOL`) or use one fixed Bukkit material. Lore is entered as `;;`-separated MiniMessage lines.

When enabled and unobstructed, the ON/OFF panel occupies the slot directly below its tool card. A custom pin can block that slot; in that case, the card itself remains clickable. The live card and panel preview uses an existing enabled definition.

## Tool editor

The tool screen edits status, name, base material, allowed worlds, category, tracking type, level requirements, and profiles. Cycling tracking type clears incompatible targets and converts each level to a GENERAL requirement with its former combined total.

Use the central preview to inspect the fully resolved item and **Give to yourself** to create a real unique instance. Deletion requires two shift-right-click confirmations within eight seconds.

## Requirement editor

Every level can use a shared GENERAL total or independent SPECIFIC target quotas.

| Control | Operation |
|---|---|
| `+1`, `+10`, `+100`, `+1000` | Increase the selected total/quota |
| `-1`, `-10`, `-100`, `-1000` | Decrease, clamped to one |
| Exact amount | Enter a positive whole number in chat |
| Mode | Convert GENERAL/SPECIFIC requirement shape |

The target selector is paginated and searchable. Material tracking shows valid blocks, crops, or fish; entity tracking shows living types. Left-click adds/edits a quota, right-click removes it, and the amount screen supports both step and exact controls.

## Level profiles

Administrators can add, duplicate, reorder, or delete levels while retaining at least one contiguous profile. The add action clones the last profile and doubles its GENERAL total or each SPECIFIC quota with overflow protection.

Each profile controls:

- Inherited or overridden MiniMessage display name.
- Inherited or overridden item material.
- Complete enchantment map and visual registry browser.
- Complete lore with line add/edit/delete/reorder and bulk `;;` input.
- Glint and custom model data. Unbreakable state, hidden enchantments, hidden attributes, and hidden additional tooltip details are permanent 3.5 protections.
- The five exact per-level abilities.

## Ability editor

Left-click toggles any ability. Right-click configures enabled EXP Booster and Potion Effect entries.

- EXP Booster accepts a multiplier from `1.0` through `100.0`.
- Potion Effect accepts `effect,level,duration_ticks,target`, for example `haste,2,100,HOLDER`.
- Potion targets are `HOLDER` or `TARGET` and effects are checked against the registry.

Auto Smelt, 3×3 Area Mine, and Magnet have no additional parameters.

## Category manager

New IDs may contain lowercase letters, numbers, `_`, or `-`. Category display names and description lines accept MiniMessage; use `;;` between description lines. Assign a tool by opening its Category control and selecting the destination.

Category slots remain unique for explicit legacy showcase routes. They no longer define the default `/pt` player menu.

## Chat prompts

Type `cancel` to return without a change. Prompts expire after 60 seconds. Every tool/category mutation is applied to a cloned configuration and becomes active only after the full candidate validates and saves.
