# PlexonTools 2.0.0

PlexonTools 2.0 is the first full release. It combines the complete per-level profile editor from the beta series with a production requirement engine designed for both broad activity totals and exact target quotas.

## Highlights

- Choose GENERAL mode for one shared block-break or mob-kill total.
- Choose SPECIFIC mode for independent quotas such as 500 Stone plus 200 Deepslate.
- Configure modes per level and edit targets through a searchable visual browser.
- Adjust totals with `±1`, `±10`, `±100`, and `±1000`, or enter an exact value in chat.
- Persist per-target progress directly on each item while retaining the required `id`, `uuid`, `level`, `stat_count`, and `bound_world` keys.
- Change display names, materials, enchantments, lore, glint, flags, unbreakable state, and custom model data at any level.
- Duplicate, reorder, or delete profiles with automatic contiguous renumbering.
- Use the standardized Plexon lore layout and new aliases including `{goal_type_description}`, `{percentage}`, `{progress_bar}`, `{bound_world}`, and `{owner_name}`.
- Keep progression hot paths memory-only; the instance registry still flushes periodically rather than per event.

## Compatibility

Existing beta configurations and items are accepted without a forced rewrite. Legacy list targets keep their shared filtered-total behavior, while native 2.0 target maps use independent quotas. The `material_upgrade` alias remains supported.

Back up the plugin directory before upgrading, replace the JAR, and restart Paper. Paper 1.21.4 and Java 21 are required. No runtime dependencies are needed.

## Known tracking behavior

Block progression is material-based. Matching player-placed blocks count because 2.0 does not store block-origin history.
