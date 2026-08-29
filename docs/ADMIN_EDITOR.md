# PlexonTools 3.0 admin editor

Open `/pt gui` with `plexontools.gui`.

## Dashboard

- **Tool Manager** opens every definition and its live preview.
- **Create New Tool** uses the held material, current world, first category, GENERAL block tracking, and a safe default profile.
- **Category Manager** creates categories and edits their MiniMessage name, icon, unique slot, and description.
- **Global Settings** toggles owner enforcement, bound-world enforcement, level-up particles, and validates the level-up sound through Paper's registry.
- **Live Player Preview** enters the category-driven player GUI.

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
- Unbreakable, glint, hidden enchantments, hidden attributes, and custom model data.
- The five exact per-level abilities.

## Ability editor

Left-click toggles any ability. Right-click configures enabled EXP Booster and Potion Effect entries.

- EXP Booster accepts a multiplier from `1.0` through `100.0`.
- Potion Effect accepts `effect,level,duration_ticks,target`, for example `haste,2,100,HOLDER`.
- Potion targets are `HOLDER` or `TARGET` and effects are checked against the registry.

Auto Smelt, 3×3 Area Mine, and Magnet have no additional parameters.

## Category manager

New IDs may contain lowercase letters, numbers, `_`, or `-`. Category display names and description lines accept MiniMessage; use `;;` between description lines. Assign a tool by opening its Category control and selecting the destination.

Category slots must be unique. The bundled layout uses `11`, `13`, `15`, and `22` for Mining, Combat, Farming, and Utility.

## Chat prompts

Type `cancel` to return without a change. Prompts expire after 60 seconds. Every tool/category mutation is applied to a cloned configuration and becomes active only after the full candidate validates and saves.
