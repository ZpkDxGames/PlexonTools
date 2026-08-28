# Architecture

## Runtime flow

1. `ToolProgressListener` reads PlexonTools PDC from the held item.
2. The tool ID resolves against the immutable in-memory definition cache.
3. `ProgressionService` validates definition state, owner, allowlisted world, and bound world.
4. Matching block or entity events advance the counter through `ProgressionMath`.
5. Normal progress refreshes dynamic name/lore. A level change or profile-hash mismatch reapplies material, enchantments, glint, flags, and custom model data.
6. `InstanceRegistry` updates its cached audit record and flushes periodically or on shutdown.

No YAML lookup or disk write occurs on block-break or mob-kill hot paths.

## Profile resolution

The root `display_name` and `base_material` seed level 1. A level may override either value; later levels inherit the most recent override. Enchantments, lore, and item settings are complete states for their individual level.

Legacy `material_upgrade` values are treated as material overrides. Structural GUI operations preserve inheritance metadata and write the v2 schema.

## Transactional configuration writes

`ToolConfigRepository` clones the active YAML in memory, applies one mutation, strictly parses every tool, and saves only after validation succeeds. The immutable definition map is swapped after the file write. Invalid GUI input therefore leaves both the disk file and active cache unchanged.

Manual reloads remain fault-tolerant: an invalid manually authored definition is logged and skipped while valid definitions still load.

## Data ownership

- `tools.yml` is the administrator-authored definition source of truth.
- Item PDC is the runtime source of truth for each physical instance.
- `data.yml` is an audit/persistence registry keyed by instance UUID.
- `config.yml` controls global enforcement, UI, effects, and progress bars.
- `messages.yml` contains MiniMessage feedback.

## PDC keys

All keys use the `plexontools` namespace:

- `id` — definition ID
- `uuid` — unique physical instance UUID
- `level` — current progression level
- `stat_count` — progress inside the current level
- `bound_world` — immutable instance world binding
- `owner` — owner UUID
- `profile_hash` — fingerprint of the last fully applied level profile

The hash is optional for compatibility. A beta.1 item without it receives a full profile refresh on its next progress event.

## Structural level edits

Profiles are stored contiguously from level 1. Duplicate inserts immediately after a source profile. Move swaps adjacent profiles. Delete removes the selected profile. Each operation rewrites the profile list with contiguous numbers; existing items retain their numeric level and therefore resolve against the newly ordered profile.
