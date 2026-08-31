# PlexonTools 3.6.1 admin editor

Open `/pt gui` with `plexontools.gui`.

## Dashboard

- **Tool Manager** opens every definition and its live preview.
- **Create New Tool** uses the held material, current world, first category, GENERAL block tracking, and a safe default profile.
- **World Tool Menus** configures the player-facing `/pt` layout for every loaded or manually named world.
- **Player Menu Appearance** edits the default tool card and ON/OFF panel used in `/pt`.
- **Category Manager** retains internal tool organization and legacy showcase metadata.
- **Global Settings** controls bound-world enforcement, the live progress action bar, and level-up effects. Permanent owner binding is displayed but cannot be disabled.
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

The tool screen edits status, name, base material, allowed worlds, category, tracking type, level requirements, and profiles. Cycling tracking type clears incompatible targets and converts each level to a GENERAL requirement with its former combined total. Dimension progression scope and its anchor remain explicit YAML controls so administrators can review this persistence decision directly.

Use the central preview to inspect the fully resolved item and **Give to yourself** to create a real unique instance. Deletion requires two shift-right-click confirmations within eight seconds.

## Requirement editor

Every level can use a shared GENERAL total or independent SPECIFIC target quotas.

Counters start at zero whenever a player enters a level. No aggregate or per-target overflow is carried from the previous level, even when both levels request the same target.

| Control | Operation |
|---|---|
| `+1`, `+10`, `+100`, `+1000` | Increase the selected total/quota |
| `-1`, `-10`, `-100`, `-1000` | Decrease, clamped to one |
| Exact amount | Enter a positive whole number in chat |
| Mode | Convert GENERAL/SPECIFIC requirement shape |

The target selector is paginated and searchable. Material tracking shows valid blocks, crops, or fish; entity tracking shows living types. Left-click adds/edits a quota, right-click removes it, and the amount screen supports both step and exact controls.

On save or reload, SPECIFIC block objectives are validated against the material resolved for that level. A pickaxe, axe, shovel, or hoe objective must use a compatible block family and sufficient harvest tier; invalid candidates are rejected without replacing the live configuration.

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

## YAML-only controls

The GUI intentionally keeps persistence tuning and the full physical-item lore template out of click/chat editing. Configure these directly in `config.yml`:

- `storage`: database filename, async flush cadence, transaction size, pending-write bound, busy timeout, WAL checkpoint size, and startup integrity check.
- `performance.progress-visual-refresh-ticks`: coalesced item-lore/PDC/action-bar refresh window from 1 through 20 ticks.
- `tool-lore.template`: the freely ordered global physical-tool layout.
- `tool-lore.requirements`: independent GENERAL, SPECIFIC, and maximum-level rows.
- `tool-lore.enchantments`: active-enchantment and empty-state row formats used by `{enchantment_lines}`.
- `progress-value-colors`: start, middle, and complete colors for dynamic current-value and percentage placeholders.
- `tools.yml` `progression.scope` and `progression.anchor_world`: shared-player or separate-world progression.

Every editable YAML includes an inline schema guide. On startup, PlexonTools also refreshes clean reference copies under `plugins/PlexonTools/examples`; compare or copy from them without replacing a customized live file wholesale. Use `/pt reload` after visual or definition edits. Restart Paper after changing storage startup options.

Use `/pt backup` before major GUI or YAML changes to create a checkpointed copy of the runtime database.
