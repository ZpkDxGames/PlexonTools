# Changelog

## 3.5.0 — 2026-08-29

- Replaced the default `/pt` category selector with persistent, per-world tool activation menus.
- Added `menus.yml` plus in-game editing for world menu titles, rows, fillers, reserved tools, and slots.
- Added persistent active/inactive state to the v4 instance registry and safe restoration across join, respawn, reload, and world transitions.
- Added permanent player binding, manual-drop cancellation, death retention, external-inventory protection, foreign-owner pickup prevention, and duplicate reconciliation.
- Made every Plexon tool unbreakable and removed the former per-level toggle from the effective runtime profile.
- Forced clean tooltip flags for enchantments, stored enchantments, attributes, unbreakable state, and additional vanilla details.
- Added `{requirement_lines}` expansion with independent SPECIFIC target rows and new per-requirement placeholders.
- Preserved legacy objective templates by expanding a SPECIFIC `{goal_type_description}` line once per target.
- Changed full-inventory behavior so protected tools are never dropped; activation remains pending until a slot is available.
- Added automatic 3.0 registry/configuration migration, refreshed documentation, and 3.5 release metadata.

## 3.0.0 — 2026-08-29

- Added Mining, Combat, Farming, Utility, and custom category navigation with `/pt <category> [player]` and `/pt all [player]` routing.
- Added `ITEMS_FARMED`, `FISH_CAUGHT`, `DAMAGE_DEALT`, and `BLOCKS_PLACED` alongside the existing block and mob trackers.
- Added per-level Auto Smelt, protected-aware 3×3 Area Mine, EXP Booster, holder/target Potion Effect, and Magnet abilities.
- Added category creation/customization/assignment, global enforcement/effect controls, and ability editing to the in-game dashboard.
- Added `category` and `stat_breakdown` PDC synchronization plus category/target snapshots in the v3 audit schema.
- Replaced synchronous registry autosaves with revisioned, atomic asynchronous checkpoints and a final shutdown flush.
- Enforced `<!italic>` normalization through the single MiniMessage component service.
- Added the structured `default_lore_format` schema, category placeholders, nested message schema, and six representative bundled tools.
- Preserved 2.0 configurations, issued items, list-filter semantics, `material_upgrade`, and legacy lore/message layouts.
- Added v3 unit coverage, operator documentation, release notes, and a dedicated 2.0 migration guide.

## 2.0.0 — 2026-08-28

- Added the GENERAL and SPECIFIC per-level requirement engine.
- Added independent persistent target counters while retaining every mandatory beta PDC key.
- Added searchable material/entity selection with visual quota editing, stepped controls, and exact input.
- Added native 2.0 root quota maps and per-level requirement overrides.
- Added compatibility parsing for beta list filters, issued items, `data.yml`, and `material_upgrade`.
- Added the standardized Plexon MiniMessage lore layout and angle-bracket placeholder aliases.
- Added dynamic objective, percentage, progress-bar, bound-world, owner-name, and target-progress placeholders.
- Added per-target overflow handling across compatible level transitions.
- Added unit coverage for shared totals, independent quotas, ignored targets, empty target sets, and overflow.
- Updated the bundled example, release metadata, administrator guide, and architecture guide for the stable release.

## 1.0.0-beta.2 — 2026-08-28

- Added complete per-level profiles with inherited MiniMessage names and materials.
- Added a visual paginated enchantment editor with click-based level adjustments and bulk input.
- Added line-by-line lore editing, insertion, deletion, reordering, clearing, and bulk replacement.
- Added level duplication, profile reordering, deletion of any level, and automatic renumbering.
- Added per-level unbreakable, glint, hidden-enchantment, hidden-attribute, and custom-model-data settings.
- Added cumulative level totals, richer profile previews, and nine new dynamic placeholders.
- Added profile hashes so existing items synchronize changed materials and metadata on their next progress event.
- Added transactional GUI configuration writes and beta.1 `material_upgrade` compatibility.
- Added release packaging through the validated GitHub Actions build.

## 1.0.0-beta.1 — 2026-08-28

- Added Paper 1.21.4 / Java 21 project foundation.
- Added PDC-backed unique item identity, ownership, world binding, levels, and progress.
- Added cached YAML tool definitions and persistent instance registry.
- Added block and mob tracking with target filters and overflow-safe advancement.
- Added MiniMessage-driven dynamic lore, progress bars, level-up sound, and particles.
- Added player showcase and in-game administrative editor GUIs.
- Added `/pt`, `/pt give`, `/pt gui`, `/pt reload`, aliases, permissions, and tab completion.
- Added unit tests and GitHub Actions build validation.
