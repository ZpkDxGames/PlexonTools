# Migrating from PlexonTools 2.0 to 3.0

1. Stop Paper and back up the complete `plugins/PlexonTools` directory.
2. Replace the old JAR with `PlexonTools-3.0.0.jar`.
3. Add `categories.yml`. The bundled file is generated automatically when missing.
4. Optionally add `category: mining` (or another valid ID) to every tool. Definitions without it use the first configured category.
5. Start Paper and review the startup counts for tools, categories, and tracked instances.
6. Run `/pt gui` to verify categories and profiles, then grant a test instance.

## Preserved formats

- 2.0 GENERAL and SPECIFIC requirements.
- Legacy `tracking.targets` lists with shared filtered-total semantics.
- `material_upgrade` as an alias for `material`.
- Existing item IDs, UUIDs, levels, progress, target breakdown, owner, and world binding.
- Existing `data.yml` records without category or target-progress fields.
- `default-lore-format.lines` when the new structured `default_lore_format` section is absent.
- Root-level `messages.yml` keys as a fallback for the new `messages:` section.

## Automatic item migration

No bulk rewrite is required. A 2.0 item has no `plexontools:category` key; it remains readable and receives the definition's current category when its item metadata next refreshes. The v3 profile hash includes ability state, so changed ability/profile definitions also trigger a complete item refresh after accepted progress.

## Recommended YAML modernization

- Assign every tool an explicit category.
- Move default lore to the structured `default_lore_format` schema.
- Use ability maps when parameters are needed; simple lists remain valid.
- Keep a world in every `allowed_worlds` list and use `/pt give ... [world]` only with one of those values.

If a definition is invalid during manual reload, PlexonTools logs and skips it while retaining the rest of the valid runtime registry. Fix the reported YAML entry and reload again.
