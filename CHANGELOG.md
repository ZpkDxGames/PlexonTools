# Changelog

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
