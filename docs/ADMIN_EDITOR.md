# Admin editor

Open the editor with `/pt gui`.

## Tool screen

- Toggle whether the definition is active.
- Edit the MiniMessage display name through chat.
- Set the base material from the cursor or held item.
- Toggle loaded worlds or enter an unloaded world name.
- Cycle between block-break and mob-kill tracking.
- Enter comma-separated block materials or entity types; `none` tracks all.
- Open the level editor or grant a test instance to yourself.

## Level screen

- Requirements accept positive whole numbers.
- Enchantments use `enchantment=level` pairs separated by commas.
- Lore lines are separated with `;;` and support all documented placeholders.
- Left-click the material-upgrade button with an item to set it; right-click to clear it.
- Only the final level can be removed, preserving contiguous numbering.

Chat prompts accept `cancel` and expire after 60 seconds.
