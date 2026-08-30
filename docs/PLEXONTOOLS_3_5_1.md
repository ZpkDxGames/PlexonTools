# PlexonTools 3.5.1 — Historical Baseline and 3.6 Roadmap

> Historical baseline: PlexonTools 3.6.1 supersedes this release. See [`PLEXONTOOLS_3_6_1.md`](PLEXONTOOLS_3_6_1.md) for the current behavior, [`PLEXONTOOLS_3_6_0.md`](PLEXONTOOLS_3_6_0.md) for the database foundation, and [`PLEXONTOOLS_3_5_2.md`](PLEXONTOOLS_3_5_2.md) for the intervening progression patch. The original roadmap below is preserved for release history.

PlexonTools 3.5.1 established persistent, player-bound and world-scoped entitlements that players can activate or deactivate through `/pt` without losing their identity, level, or progress.

This document records the current implementation and the planned direction for the next release. Roadmap items are goals rather than guarantees until profiling, implementation, and compatibility testing are complete.

## Release profile

| Item | Current value |
|---|---|
| Version | `3.5.1` |
| Server platform | Paper `1.21.4` |
| Java version | Java `21` |
| Runtime dependencies | None |
| Runtime data format | `data.yml`, registry schema v4 |
| Configuration formats | YAML and in-game GUI editors |
| Default player route | Per-world activation menu through `/pt` |

## What 3.5.1 changed

### Allowed-world visibility fix

An enabled tool now appears automatically in the `/pt` menu when its `allowed_worlds` list contains the player's current world. Assigning a custom world such as `Survival_World` no longer requires a second, hidden tool reservation in `menus.yml`.

- World comparison is case-insensitive.
- The live Bukkit world name keeps its original capitalization in the interface.
- Entries in `menus.yml` pin tools to exact slots instead of acting as a second allowlist.
- Administrators can restore strict membership with `world-menu.auto-show-allowed-tools: false`.
- In strict mode, a tool must be both allowed in `tools.yml` and pinned in the world's `menus.yml` section.

### Customizable player tool cards

The player-facing tool card can now be customized globally in `config.yml`:

- Dynamic saved-level material through `TOOL`, or one fixed Bukkit material.
- MiniMessage display name.
- MiniMessage lore lines.
- Optional enchantment glint while active.
- Normal item, progression, owner, world, requirement, and category placeholders.
- Menu-state placeholders: `status`, `state`, `state_symbol`, `toggle_action`, and `toggle_hint`.

### Dedicated ON/OFF panels

When layout space is available, `/pt` renders a separate toggle panel directly below each tool card.

- The active state has an independent material, name, and lore template.
- The inactive state has an independent material, name, and lore template.
- Clicking the panel activates or safely stores the associated tool.
- If a custom pinned layout blocks the lower slot, the tool card remains clickable.
- Automatic placement reserves card-and-panel pairs to avoid collisions.

### In-game appearance editor

Administrators can open `/pt gui` and select **Player Menu Appearance** to edit:

- Automatic or strict allowed-world visibility.
- Tool-card material, display name, lore, active glint, and live preview.
- ON/OFF panel availability.
- Active and inactive panel materials, names, and lore.

Lore entered through the editor uses `;;` to separate lines and accepts MiniMessage formatting and supported placeholders.

### Refreshed bundled defaults

Fresh installations use a coordinated gold Legendary theme for player menus, categories, messages, progress bars, and physical tool lore. The bundled `tools.yml` defines one `legendary_pickaxe` with a complete level 1–100 progression for `Survival_World`, and `menus.yml` pins it in the matching `survival_world` layout. Runtime `data.yml` is still generated from live player state and is not distributed with sample records.

## Current feature set

### Persistent tool lifecycle

Each issued tool has a unique instance UUID and is permanently associated with:

- One tool definition.
- One owner UUID.
- One bound world.
- One current level.
- Aggregate and optional per-target progress.
- Active or inactive entitlement state.

Deactivation stores the latest state and removes the physical item. Reactivation reconstructs the same logical instance rather than creating a fresh tool. Join, respawn, reload, and world-change reconciliation restore active tools when the player is in the correct world and has inventory space.

### Permanent protection

Plexon tools are protected as bound progression items:

- Always unbreakable.
- Cannot be manually dropped with Q or Ctrl+Q.
- Retained on death without duplicating through normal drops.
- Cannot be transferred into external inventories.
- Cannot be picked up or used by another player.
- Temporarily leave the inventory outside their bound world.
- Never become a dropped item when an inventory is full.

### Progression and requirements

Every level can use one of two requirement models:

- `GENERAL`: one shared total for accepted activity.
- `SPECIFIC`: independent mandatory quotas for every configured target.

Compatible overflow can pass into later contiguous levels. Tool profiles can change their display name, material, enchantments, lore, glint, custom model data, requirement, and abilities at each level.

Supported tracking types are:

1. Blocks broken.
2. Mobs killed.
3. Items farmed.
4. Fish caught.
5. Damage dealt.
6. Blocks placed.

### Lore and tooltip formatting

The structured `default_lore_format` in `config.yml` controls the default physical item lore. The special `{requirement_lines}` entry expands into:

- One summarized line for a GENERAL requirement.
- One independently formatted line for each SPECIFIC target.

PlexonTools hides unnecessary vanilla tooltip information while preserving functional enchantments and glint:

- Enchantment and stored-enchantment text.
- Attribute text.
- The unbreakable label.
- Additional item-specific tooltip details.
- Can-break, can-place-on, dye, and armor-trim details when applicable.

### Per-level abilities

| Ability | Behavior |
|---|---|
| Auto Smelt | Converts supported drops to their furnace result |
| Area Mine 3×3 | Breaks an orientation-aware plane while respecting cancellable protection checks |
| EXP Booster | Multiplies supported mining, combat, and fishing experience |
| Mob Potion Effect | Applies a configured effect to the holder or target |
| Magnet | Moves supported drops into the owner's inventory while preserving overflow |

### Administrative configuration

The `/pt gui` dashboard includes editors for:

- Tool definitions and enabled state.
- Allowed worlds.
- Tracking types and requirements.
- Per-level profiles and abilities.
- World menu title, size, filler, and pinned slots.
- Player tool-card and toggle-panel appearance.
- Internal categories and global settings.

Manual YAML editing remains available through `config.yml`, `tools.yml`, `menus.yml`, `categories.yml`, and `messages.yml`. `/pt reload` validates and reloads them.

### Commands and permissions

| Command | Permission | Purpose |
|---|---|---|
| `/pt` | `plexontools.use` | Open the current world's activation menu |
| `/pt <category> [player]` | `plexontools.use`; targeting another player requires `plexontools.admin` | Open a legacy category showcase |
| `/pt all [player]` | `plexontools.use`; targeting another player requires `plexontools.admin` | Open the unified legacy showcase |
| `/pt give <player> <tool_id> [world]` | `plexontools.give` | Issue one unique instance bound to an allowed world |
| `/pt gui` | `plexontools.gui` | Open the administrative dashboard |
| `/pt reload` | `plexontools.reload` | Validate and reload plugin configuration |

### Files

| File | Purpose |
|---|---|
| `config.yml` | Enforcement, effects, progress bars, physical default lore, and player-menu templates |
| `tools.yml` | Tool definitions, tracking, requirements, level profiles, and abilities |
| `menus.yml` | Per-world `/pt` layouts and exact slot pins |
| `categories.yml` | Internal category presentation and legacy showcase metadata |
| `messages.yml` | MiniMessage feedback templates |
| `data.yml` | Generated schema-v4 instance and activation recovery registry |

## Player-menu configuration example

```yaml
world-menu:
  auto-show-allowed-tools: true

  tool-card:
    material: TOOL
    display-name: "{tool}"
    glint-when-active: true
    lore:
      - "<dark_gray>{tool_id}</dark_gray>"
      - ""
      - "<gray>Level:</gray> <yellow>{level}/{max_level}</yellow>"
      - "<gray>Progress:</gray> <aqua>{current}</aqua><dark_gray>/</dark_gray><green>{required}</green>"
      - "<gray>Status:</gray> {status}"
      - "{toggle_hint}"

  toggle-panel:
    enabled: true
    active:
      material: LIME_STAINED_GLASS_PANE
      display-name: "<green><bold>✔ ENABLED</bold></green>"
      lore:
        - "<yellow>Click to deactivate and store this tool.</yellow>"
    inactive:
      material: RED_STAINED_GLASS_PANE
      display-name: "<red><bold>✘ DISABLED</bold></red>"
      lore:
        - "<green>Click to activate this tool.</green>"
```

## Current persistence model

The materialized item stores its compact gameplay state in Paper's Persistent Data Container. `data.yml` provides the activation entitlement and recovery registry for inactive or temporarily unavailable tools.

Registry schema v4 stores information such as:

- Instance, owner, tool, category, and bound-world identifiers.
- Current level, total progress, and per-target progress.
- Active and menu-managed state.
- Owner name and lifecycle timestamps.
- Lifetime tracked activity.

Snapshots are generated from an in-memory concurrent registry and written asynchronously. Shutdown performs a final blocking checkpoint after gameplay handlers stop.

## Known next-release priorities

This section records the plan that guided **PlexonTools 3.6.0**. Database-backed runtime persistence and gameplay-thread I/O separation shipped in 3.6.0; the profiling and allocation-reduction items remain useful targets for continued measurement.

### 1. Performance and TPS optimization

Frequent events—especially block breaking, progression updates, item metadata reconstruction, ability execution, and protection integration—can create measurable server-thread pressure. The next release should begin with profiling rather than assuming one cause.

Planned work:

- Establish repeatable baselines with Paper timings and a profiler such as spark.
- Measure normal mining, high-Haste mining, Auto Smelt, Magnet, and Area Mine 3×3 independently.
- Add internal timing counters around event validation, progression calculation, lore rendering, PDC mutation, registry updates, and abilities.
- Reject irrelevant events as early as possible before definition, PDC, or profile work.
- Cache immutable, prevalidated definition data used by hot event paths.
- Reduce temporary collections, repeated placeholder maps, and avoidable component allocations.
- Avoid rebuilding full item lore and metadata when the visible profile has not changed.
- Coalesce safe visual refreshes while keeping authoritative progress accurate.
- Review Area Mine protection checks, drop calculation, and recursive event flow for duplicated work.
- Batch persistence updates outside the gameplay event without moving unsafe Bukkit or Paper API calls off the server thread.
- Add regression benchmarks for single-player and concurrent-player mining workloads.

Optimization must preserve protection-plugin compatibility, exact progression, item ownership, drops, abilities, and recovery behavior. Bukkit inventory, world, entity, and item operations will remain on the server thread unless Paper explicitly documents an operation as asynchronous-safe.

### 2. Dedicated database storage

The next release should replace mutable `data.yml` runtime persistence with a dedicated SQLite database, provisionally named `plexontools.db`.

The initial database scope includes all mutable player and custom-tool instance state currently recorded in `data.yml`:

- Players and cached owner names.
- Tool-instance UUIDs and ownership.
- Bound worlds and categories.
- Active and menu-managed entitlements.
- Levels and aggregate progress.
- Per-target progress.
- Lifetime totals and timestamps.

Administrator-authored definitions and visual defaults should remain in human-readable YAML during the first database migration. This keeps `tools.yml`, `config.yml`, `menus.yml`, `categories.yml`, and `messages.yml` easy to edit, review, export, and version-control. A later release can evaluate database-backed definitions separately if there is a demonstrated benefit.

Planned database architecture:

- SQLite with Write-Ahead Logging where the host filesystem supports it.
- Explicit schema-version and migration metadata.
- Prepared statements and transactional writes.
- Indexed lookups for owner, instance UUID, tool, bound world, and active state.
- A bounded asynchronous write queue that batches compatible updates.
- An in-memory gameplay cache so normal block events never wait for disk I/O.
- Transactional startup loading and graceful shutdown draining.
- Periodic database checkpoints and operator-controlled backup support.
- Integrity checks and actionable startup errors instead of partial silent loading.

SQLite improves consistency and recovery through transactions and constraints, but it is not encryption. File permissions and protected backups remain important; encrypted storage would require a separately evaluated solution such as SQLCipher.

### Migration from `data.yml`

The migration must be automatic, safe, and repeatable:

1. Detect an existing schema-v4 `data.yml` when no initialized database exists.
2. Parse and validate every record before modifying either source.
3. Import all valid records in one database transaction.
4. Verify counts, identifiers, ownership, progress, and active states.
5. Preserve a timestamped backup of the original YAML.
6. Commit a migration marker only after verification succeeds.
7. Leave the original data and previous runtime behavior untouched if any critical step fails.

Repeated startup after a successful migration must not create duplicate instances or re-import stale records.

## Proposed database entities

| Entity | Purpose |
|---|---|
| `schema_metadata` | Database version, migration state, and compatibility metadata |
| `players` | Player UUID and cached name information |
| `tool_instances` | Ownership, definition ID, bound world, level, progress, state, and timestamps |
| `target_progress` | Normalized SPECIFIC progress keyed by instance and target |

Additional audit or history tables should only be added when they provide an operator-facing recovery or diagnostic benefit. Unbounded event-by-event logging would increase storage and write pressure and is not part of the default plan.

## Next-release acceptance criteria

The performance and database work should not be considered complete until it meets these conditions:

- Profiling demonstrates lower server-thread cost in the targeted tool workloads.
- Normal tool actions perform no YAML or database I/O on the gameplay thread.
- Progress remains correct through rapid events, level transitions, world changes, relogs, restarts, and full inventories.
- Database writes survive forced shutdown and recovery tests without duplicate or lost instances.
- `data.yml` migration is idempotent and preserves a recoverable backup.
- Existing 3.5.1 items retain their UUID, owner, bound world, level, progress, target counters, category, and active state.
- Database schema upgrades are versioned and covered by migration tests.
- Protection, ability, GUI, and progression regression tests remain green.
- Documentation explains backup, restore, migration, and rollback procedures.

## Direction summary

| Area | PlexonTools 3.5.1 | Planned next release |
|---|---|---|
| World visibility | `allowed_worlds` with optional strict pins | Preserve behavior |
| Player controls | Configurable tool card and ON/OFF panel | Preserve and profile GUI refresh cost |
| Runtime state | In-memory registry plus asynchronous `data.yml` snapshots | In-memory cache plus transactional SQLite writes |
| Hot event path | Synchronous Paper event processing | Profiled, allocation-reduced, and persistence-decoupled processing |
| Migration | Registry schema v3 to v4 | Automatic schema-v4 YAML to versioned database migration |
| Safety | Owner/world protection and recovery | Same guarantees with stronger transactional integrity |

The next release should prioritize measurable TPS improvement and data integrity without changing the player-facing progression model that 3.5.1 established.
