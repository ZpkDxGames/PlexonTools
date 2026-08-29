# PlexonTools 3.0.0

PlexonTools 3.0 is a complete Paper 1.21.4 revamp. It expands the 2.0 requirement engine into a category-driven tool ecosystem with six progression sources, five per-level abilities, a new administrator dashboard, and asynchronous registry persistence.

## Highlights

- Browse Mining, Combat, Farming, Utility, or custom categories through `/pt`, `/pt <category>`, and `/pt all`.
- Track `BLOCKS_BROKEN`, `MOBS_KILLED`, `ITEMS_FARMED`, `FISH_CAUGHT`, `DAMAGE_DEALT`, or `BLOCKS_PLACED`.
- Configure GENERAL totals or SPECIFIC material/entity quotas independently on every level.
- Unlock Auto Smelt, 3×3 Area Mine, EXP Booster, holder/target potion effects, and Magnet by level.
- Create categories, assign tools, edit global enforcement/effects, and manage abilities without leaving the game.
- Keep event-path state in item PDC and memory while `data.yml` checkpoints on an asynchronous scheduler.
- Normalize all Adventure MiniMessage output with `<!italic>` to remove Paper's default custom-item italics.

## Compatibility

PlexonTools 2.0 tools and issued items remain readable. Missing category PDC is populated from the definition on the next item refresh. Existing list-style target filters keep their shared-total behavior; map-style targets remain independent quotas. The old `default-lore-format.lines` key is still accepted when the new structured `default_lore_format` section is absent.

Back up the plugin directory before upgrading. Add `categories.yml`, assign each tool a category if desired, replace the JAR, and restart Paper. See [the migration guide](docs/MIGRATION_3.md) for the exact sequence.

Paper 1.21.4 and Java 21 are required. There are no runtime dependencies.
