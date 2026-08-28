# Admin editor

Open the editor with `/pt gui`.

## Tool screen

- Enable or disable the definition.
- Edit the root MiniMessage display name.
- Set the fallback material from the cursor or held item.
- Toggle loaded worlds or add an unloaded world by name.
- Cycle between block-break and mob-kill tracking.
- Set comma-separated target materials or entity types; `none` tracks all.
- Open the profile list, preview the starting item, or grant a test instance.

## Level list

Each icon is rendered with that level's resolved material and shows its display name, cumulative progress before the level, next threshold, and enchantments.

The add button clones the final profile and doubles its threshold. Open any profile for detailed controls.

## Level profile

- **Display name:** left-click to set a MiniMessage override; right-click to inherit the prior value.
- **Material:** left-click with a cursor or held item to create a change at this level; right-click to inherit.
- **Threshold:** enter the progress needed to reach the following level.
- **Enchantments:** open the visual enchantment browser or bulk text editor.
- **Lore:** edit individual MiniMessage lines or replace the whole list.
- **Item properties:** toggle unbreakable, glint mode, hidden enchantments, hidden attributes, and custom model data.
- **Level structure:** duplicate after, move earlier/later, or shift-right-click delete. Later levels are renumbered.
- **Navigation:** move directly to the previous or next profile while editing.

The central item is an exact preview using zero current progress and the first allowed world.

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

The add button appends one line. The bulk editor uses `;;` between lines. Empty lore is supported. Clear-all operations require shift-right-click.

Chat prompts accept `cancel` and expire after 60 seconds. Every mutation is parsed and validated against a temporary configuration before `tools.yml` is replaced.
