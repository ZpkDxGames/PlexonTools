# Admin editor

Open the editor with `/pt gui`.

## Tool screen

- Enable or disable the definition.
- Edit the root MiniMessage display name.
- Set the fallback material from the cursor or held item.
- Toggle loaded worlds or add an unloaded world by name.
- Cycle between block-break and mob-kill tracking. Changing the type clears incompatible targets and converts levels to GENERAL totals.
- Open the level 1 requirement engine directly, or manage every profile from the level list.
- Preview the resolved starting item or grant a test instance.

## Requirement editor

Every level may use a different mode.

- **GENERAL:** one amount counts all activity of the configured tracking type.
- **SPECIFIC:** every selected material or living entity has an independent quota, and all quotas must complete.

Click the mode button to switch. Switching to GENERAL preserves the prior combined total. Switching to SPECIFIC starts with an empty target set, so select at least one target before players can advance.

GENERAL amounts and individual SPECIFIC quotas provide the same controls:

| Control | Operation |
|---|---|
| `+1`, `+10`, `+100`, `+1000` | Increase the amount |
| `-1`, `-10`, `-100`, `-1000` | Decrease, clamped to 1 |
| Exact amount | Enter any positive whole number in chat |

## Target selector

The browser lists every valid block material or living entity for the tool's tracking type.

- Left-click an unselected target to add it with a default quota of 100 and open its amount editor.
- Left-click a selected target to edit its amount.
- Right-click a selected target to remove it.
- Use search to filter by any substring, such as `ORE`, `DEEPSLATE`, or `SKELETON`.
- Clear search to restore the complete paginated list.

The target amount screen also provides step controls, exact entry, and a shift-right-click removal action.

## Level list and profiles

Each level icon uses its resolved material and shows its name, requirement mode, cumulative required progress, combined threshold, and enchantments. The add button clones the final profile and doubles its total or every individual quota with saturation protection.

Within a level profile:

- **Display name:** left-click to set a MiniMessage override; right-click to inherit the prior value.
- **Material:** left-click with a cursor or held item to create a change at this level; right-click to inherit.
- **Requirement engine:** configure mode, shared total, target selection, and target quotas.
- **Enchantments:** open the visual registry browser or bulk text editor.
- **Lore:** edit individual MiniMessage lines or replace the whole list.
- **Item properties:** toggle unbreakable, glint mode, hidden enchantments, hidden attributes, and custom model data.
- **Level structure:** duplicate after, move earlier/later, or shift-right-click delete. Later levels are renumbered.
- **Navigation:** move directly to the previous or next profile while editing.

The central item is a resolved preview using zero progress and the first allowed world.

## Enchantment editor

The browser includes every enchantment in Paper's registry.

| Input | Operation |
|---|---|
| Left-click | Add one level |
| Shift-left-click | Add five levels |
| Right-click | Remove one level |
| Shift-right-click | Remove the enchantment |

Levels are clamped to 0–255. The bulk editor accepts `efficiency=3, unbreaking=2`; use `none` to clear the profile.

## Lore editor

| Input | Operation |
|---|---|
| Left-click line | Edit it in chat |
| Right-click line | Delete it |
| Shift-left-click line | Move it up |
| Shift-right-click line | Move it down |

The add button appends one line. The bulk editor uses `;;` between lines. Empty lore is supported. Both `{placeholder}` and `<placeholder>` syntax are accepted. Clear-all operations require shift-right-click.

Chat prompts accept `cancel` and expire after 60 seconds. Every mutation is parsed and validated against a temporary complete configuration before `tools.yml` is replaced.
