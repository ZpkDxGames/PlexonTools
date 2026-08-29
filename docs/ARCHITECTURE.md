# Architecture

## Runtime flow

1. `ToolProgressListener` reads PlexonTools PDC from the held item.
2. The tool ID resolves against the immutable in-memory definition cache.
3. `ProgressionService` validates definition state, owner, allowlisted world, and bound world.
4. The current `LevelRequirement` decides whether the material or entity is accepted.
5. `RequirementProgression` increments the GENERAL counter or the matching SPECIFIC counter and carries compatible overflow through contiguous levels.
6. Normal progress refreshes dynamic name/lore and PDC. A level change or profile-hash mismatch reapplies material, enchantments, glint, flags, and custom model data.
7. `InstanceRegistry` updates its cached audit record and flushes periodically or on shutdown.

No YAML lookup or disk write occurs on block-break or mob-kill hot paths. Lore is reserialized only after accepted progress changes or profile synchronization.

## Requirement model

`LevelRequirement` is an immutable value containing:

- `mode`: GENERAL or SPECIFIC.
- `amount`: the GENERAL threshold.
- `targets`: normalized target quota mappings. In native SPECIFIC mode these are independent quotas.

Legacy beta list filters are represented internally as GENERAL requirements with an accepted-target set. This preserves their original “any listed target contributes to one total” semantics without weakening native GENERAL mode, which accepts every target.

For SPECIFIC mode, aggregate lore progress caps each counter at its quota. Completion requires every configured target. An empty SPECIFIC requirement is intentionally incomplete.

## Configuration resolution

The root `tracking.mode`, `tracking.amount`, and `tracking.targets` establish defaults. A level may override its mode, GENERAL threshold, or SPECIFIC `requirements` map.

The root `display_name` and `base_material` seed level 1. A level may override either value; later levels inherit the most recent override. Enchantments, lore, and item settings are complete states for their individual level. Missing lore uses `default-lore-format.lines` from `config.yml`.

Legacy `material_upgrade` values are treated as material overrides. Structural GUI operations preserve inheritance metadata and write the native 2.0 per-level requirement schema.

## Transactional configuration writes

`ToolConfigRepository` clones the active YAML in memory, applies one mutation, strictly parses every tool, and saves only after validation succeeds. The immutable definition map is swapped after the file write. Invalid GUI input therefore leaves both the disk file and active cache unchanged.

Manual reloads remain fault-tolerant: an invalid manually authored definition is logged and skipped while valid definitions still load.

## Data ownership

- `tools.yml` is the administrator-authored definition source of truth.
- Item PDC is the runtime source of truth for each physical instance.
- `data.yml` is a periodically flushed audit registry keyed by instance UUID.
- `config.yml` controls enforcement, UI, effects, progress bars, and default lore.
- `messages.yml` contains MiniMessage feedback.

## PDC keys

All keys use the `plexontools` namespace:

- `id` — definition ID; mandatory.
- `uuid` — unique physical instance UUID; mandatory.
- `level` — current progression level; mandatory.
- `stat_count` — aggregate current-level progress; mandatory.
- `bound_world` — immutable instance world binding; mandatory.
- `stat_breakdown` — compact per-target counts for SPECIFIC mode; optional and absent on beta items.
- `owner` — owner UUID.
- `profile_hash` — fingerprint of the last fully applied level profile.

Optional keys are additive. A beta item without a breakdown starts with an empty map. An item without a profile hash receives a full profile refresh when its progress next changes.

## Structural level edits

Profiles are stored contiguously from level 1. Duplicate inserts after a source profile. Move swaps adjacent profiles. Delete removes the selected profile. Each operation rewrites the profile list with contiguous numbers; existing items retain their numeric level and resolve against the newly ordered profile.

Adding a GENERAL level doubles its amount. Adding a SPECIFIC level doubles every target quota. Both operations saturate at `Long.MAX_VALUE`.
