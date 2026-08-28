# PlexonTools 1.0.0-beta.2

This release turns level rewards into complete, independently editable item profiles.

## Highlights

- Set a custom MiniMessage display name and material at any level, with inheritance from earlier levels.
- Configure enchantments visually: left/right click adjusts levels, shift-click changes faster or removes an enchantment.
- Edit lore one line at a time, including add, edit, delete, reorder, clear, and bulk replacement operations.
- Duplicate, move, or delete any level profile; later levels are renumbered automatically.
- Toggle unbreakable state, glint behavior, hidden enchantments, hidden attributes, and custom model data per level.
- Preview the fully resolved item for every level directly in the editor.
- Use new dynamic placeholders: `{remaining}`, `{percent}`, `{total}`, `{next_level}`, `{level_name}`, `{tracking}`, `{targets}`, `{material}`, and `{enchantments}`.
- Existing beta.1 `tools.yml`, item PDC, and `data.yml` remain compatible. Legacy `material_upgrade` entries are still accepted.
- GUI configuration writes are validated transactionally before replacing `tools.yml`.

Paper 1.21.4 and Java 21 are required. No runtime dependencies are needed.
