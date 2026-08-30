# PlexonTools 3.5.2 — Per-Level Progress and GUI Isolation

> Historical patch documentation. PlexonTools 3.6.1 retains these behaviors and replaces live `data.yml` persistence with SQLite; see [`PLEXONTOOLS_3_6_1.md`](PLEXONTOOLS_3_6_1.md).

PlexonTools 3.5.2 is a compatibility patch for Paper 1.21.4 and Java 21. It keeps the 3.5.1 entitlement, protection, world-menu, and registry behavior while changing progression boundaries and strengthening GUI ownership.

## Release profile

| Item | Value |
|---|---|
| Version | `3.5.2` |
| Server platform | Paper `1.21.4` |
| Java version | Java `21` |
| Registry format | `data.yml`, schema v4 |
| Runtime dependencies | None |

## Per-level progress isolation

GENERAL and SPECIFIC counters belong only to the player's current level. When its requirement completes:

1. The tool advances by exactly one level.
2. Aggregate progress resets to zero.
3. Every per-target counter resets to zero.
4. Excess activity from the completing event is discarded.

For example, these profiles require 1,000 Stone breaks in two independent batches:

```yaml
levels:
  1:
    requirement_mode: SPECIFIC
    requirements:
      STONE: 500
  2:
    requirement_mode: SPECIFIC
    requirements:
      STONE: 500
```

The 500th break completes level 1 and level 2 begins at `0/500`. A large single event, such as a damage value exceeding the remaining amount, can never skip levels.

An explicitly empty `requirements: {}` map remains empty instead of inheriting root targets. The bundled level 100 profile uses this to stop progression feedback at maximum level.

Lifetime activity in the registry remains cumulative for auditing; it does not satisfy level requirements.

## Live action-bar progress

Accepted requirement activity displays progress immediately without opening the item tooltip:

```yaml
effects:
  progress-action-bar: true
```

The default text is customizable in `messages.yml`:

```yaml
messages:
  progress-update: "<#FFD54F><bold>Lv. {level}</bold></#FFD54F> <dark_gray>•</dark_gray> {progress_bar} <white>{current}</white><dark_gray>/</dark_gray><#AEEA00>{required}</#AEEA00> <gray>({percentage}%)</gray>"
```

The action bar updates only for accepted targets. Cancelled events, unrelated blocks/entities, invalid items, wrong owners, and wrong worlds produce no progress display. The normal level-up action bar takes precedence when a requirement completes.

## GUI ownership fix

PlexonTools now claims clicks and drags in inventories backed by `PlexonGuiHolder` at `LOWEST` event priority and explicitly denies the inventory result. All paginated PlexonTools views open their requested page on the next server tick, and navigation buttons use plugin-specific names and materials.

This prevents a generic page listener from another plugin—such as GhostBlocks Remastered—from interpreting a PlexonTools level-page arrow and replacing the intended GUI.

## Compatibility

- No manual YAML or `data.yml` migration is required.
- Existing UUIDs, owners, bound worlds, levels, current counters, target maps, activation state, and lifetime totals remain intact.
- New level transitions use reset semantics immediately after upgrading to 3.5.2.
- Existing configurations missing the action-bar key receive the enabled in-memory default; existing `messages.yml` files receive the built-in progress format.
