# Changelog

## Unreleased

- Forced a complete item-profile refresh for tools previously rendered by a JAR that did not understand the dynamic lore placeholders, and exposed the active build in `/pt reload` feedback to make stale binaries immediately visible.
- Reworked the default physical lore into a narrower ancient-relic layout, replaced Realm Binding with configurable Enchantments, and shortened the heading to Objectives.
- Added dynamic `current_color`, `percentage_color`, and `requirement_current_color` placeholders with a configurable red → amber → green progression while required totals keep a fixed template color.
- Added full 100-level Legendary Sword, Axe, and Shovel defaults alongside the existing 100-level Pickaxe, all sharing player progression across the survival Overworld, Nether, and End.
- Reordered the default `/pt` armory to Sword, Pickaxe, Axe, and Shovel, with paired activation panels and themed dimension layouts.
- Added transactional block-objective validation against the resolved vanilla tool family and harvest tier, plus corrected early wooden/stone Pickaxe goals that were unreachable with their current material.

## 3.6.1 — 2026-08-30

- Added configurable `PLAYER` and `WORLD` progression scopes. `PLAYER` uses one owner/tool record across every allowed dimension, while `WORLD` retains independent records.
- Added `progression.anchor_world` and deterministic legacy consolidation: an existing anchor-world record wins; otherwise the most-progressed copy is selected and rebound to the anchor.
- Made multi-world definitions without an explicit scope default to shared `PLAYER` progression, while omitted scope on existing single-world definitions remains `WORLD` for compatibility.
- Expanded the bundled Legendary Pickaxe to `Survival_World`, `Survival_World_nether`, and `Survival_World_the_end` with Overworld-anchored shared progression.
- Kept gameplay progress authoritative in the in-memory registry on every accepted event while coalescing item PDC, lore, and progress-action-bar refreshes per instance.
- Added `performance.progress-visual-refresh-ticks` with a default four-tick window and immediate visual flushes for level-ups and lifecycle boundaries.
- Retained periodic asynchronous SQLite batches and the final synchronous shutdown drain so optimization does not trade away crash safety.
- Collapsed repeated held-item identity checks into one PDC inspection per event and reused validated block/damage contexts through `MONITOR`, while keeping registry state authoritative over stale item snapshots.
- Skipped block-drop resolution entirely when no enabled tool defines Auto Smelt or Magnet, stopped terminal levels from generating progress writes, and removed empty-map/visual-marker churn from rapid GENERAL progression.
- Cached immutable lore placeholder fragments and replaced repeated per-placeholder string scans with a one-pass renderer that preserves MiniMessage tags and tag arguments.
- Changed overflow protection to write one full SQLite snapshot followed by queued deltas instead of repeating full snapshots under continuous activity; failed snapshots are re-queued in full.
- Replaced generic GUI arrow items with PlexonTools-specific spectral arrows and added a short guarded transition that rejects external inventory opens triggered by PlexonTools clicks.
- Extended GUI isolation for the lifetime of a PlexonTools inventory and fixed targeted showcase commands so the administrator remains the viewer while the selected player remains the subject.
- Fixed fat-JAR packaging to use a standard central directory; forced ZIP64 output could truncate the final SQLite native entry while still reporting a successful Gradle task. `check` now fully reads the archive and verifies Linux, macOS, and Windows SQLite natives.
- Added regression coverage for shared-dimension selection, per-level reset behavior, GUI priorities/session cleanup, coalesced-refresh configuration, one-pass placeholder rendering, YAML defaults, and SQLite durability.

## 3.6.0 — 2026-08-29

- Replaced mutable `data.yml` runtime snapshots with generated `plexontools.db` SQLite storage.
- Added schema metadata, normalized player/instance/target tables, foreign-key constraints, indexed owner/tool/world/state lookups, integrity validation, and WAL with a safe filesystem fallback.
- Coalesced repeated tool-UUID mutations in memory and persisted bounded prepared-statement batches asynchronously, keeping database and YAML I/O out of normal gameplay events.
- Cached immutable profile fingerprints and cumulative level prefixes so accepted events do not repeatedly sort enchantments or rescan every earlier level.
- Added strict, transactional, idempotent schema-v3/v4 `data.yml` migration with complete pre-validation, timestamped source backup, exact post-import verification, and a committed migration marker.
- Added graceful shutdown draining/WAL checkpointing plus `/pt backup` and `plexontools.backup` for consistent operator-controlled database backups.
- Replaced the fixed assembled lore layout with a freely reorderable `tool-lore.template` and separate GENERAL, SPECIFIC, and maximum requirement-row formats.
- Added root-level per-tool lore inheritance while retaining per-level overrides, legacy `default_lore_format`, and `default-lore-format.lines` compatibility.
- Expanded all editable bundled YAML files with schema explanations, accepted values, behavior notes, placeholder references, and customization examples.
- Added refreshed non-authoritative configuration references under the runtime `examples/` directory without overwriting live administrator files.
- Added real SQLite round-trip, index, WAL, abrupt-process recovery, backup, foreign-key corruption, invalid-legacy-data, and idempotent migration regression tests.

## 3.5.2 — 2026-08-29

- Changed level advancement so aggregate and per-target progress reset to zero at every level boundary; excess activity never satisfies the next level.
- Limited each accepted gameplay event to at most one level-up, including large damage increments.
- Preserved explicitly empty SPECIFIC requirement maps so terminal profiles do not inherit root targets or continue tracking after maximum level.
- Added a configurable live progress action bar after every accepted requirement event through `effects.progress-action-bar` and `messages.progress-update`.
- Updated the level browser and editor to state explicitly that progress starts at zero for each level.
- Claimed PlexonTools inventory clicks and drags at Bukkit's earliest event priority and marked them denied before other plugins process them.
- Deferred pagination transitions by one tick and gave navigation buttons PlexonTools-specific labels/materials to prevent cross-plugin GUI routing.
- Added regression coverage for repeated same-target quotas, discarded overflow, progress-feedback defaults, and GUI listener priority.

## 3.5.1 — 2026-08-29

- Fixed allowed tools not appearing in custom worlds such as `Survival_World` unless admins also created a separate `menus.yml` reservation.
- Added `world-menu.auto-show-allowed-tools`; enabled by default while strict explicit membership remains optional.
- Added configurable tool-card material, display name, lore, active glint, and placeholder rendering in `config.yml`.
- Added separately configurable active/inactive toggle panels directly beneath tool cards when layout space is available.
- Added an in-game **Player Menu Appearance** editor for tool cards, ON/OFF panels, materials, names, lore, glint, and availability behavior.
- Reframed `menus.yml` tool entries as exact layout pins and made automatic placement panel-aware.
- Preserved loaded-world capitalization in the administrative world list.
- Refreshed the bundled YAML defaults with a gold Legendary theme and one complete 100-level `legendary_pickaxe` progression for `Survival_World`.

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
