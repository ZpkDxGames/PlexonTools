# Architecture

## Runtime flow

1. `ToolProgressListener` checks the held item for PlexonTools PDC data.
2. The tool ID resolves against the immutable in-memory definition cache.
3. `ProgressionService` validates the owner, definition allowlist, and bound world.
4. Matching block or entity events advance the counter using `ProgressionMath`.
5. Normal progress refreshes only that `ItemStack`'s counter and dynamic lore; level-ups additionally apply material, name, and enchantment rewards.
6. `InstanceRegistry` updates its in-memory record and flushes periodically or on shutdown.

No YAML lookup or disk write occurs on every block break or kill.

## Data ownership

- `tools.yml` is the administrator-authored source of truth for definitions.
- Item PDC is the runtime source of truth for each physical instance.
- `data.yml` is an audit/persistence registry keyed by the instance UUID.
- `config.yml` controls global enforcement, UI, effects, and progress-bar presentation.
- `messages.yml` contains MiniMessage user feedback.

## PDC keys

All keys use the `plexontools` namespace:

- `id` — definition ID
- `uuid` — individual instance UUID
- `level` — current progression level
- `stat_count` — progress within the current level
- `bound_world` — exact instance world binding
- `owner` — owner UUID used for enforcement

## Editor safety

The GUI writes through `ToolConfigRepository`, validates typed values, saves `tools.yml`, and rebuilds the immutable definition cache. Tool deletion requires two shift-right-click confirmations within eight seconds. Existing items whose definition is deleted become inactive and are rejected by the event layer.
