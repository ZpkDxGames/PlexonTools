# PlexonTools 3.6.1 — Performance and Multi-Dimension Guide

PlexonTools 3.6.1 keeps the 3.6.0 SQLite schema and adds shared dimension progression, a lower-cost block progression path, and stronger GUI transition ownership.

## Release contract

| Area | Value |
|---|---|
| Plugin version | `3.6.1` |
| Server | Paper `1.21.4` |
| Java | `21` |
| Runtime state | `plugins/PlexonTools/plexontools.db` |
| Database schema | `1` (unchanged) |
| Shared-progress boundary | Owner UUID + tool ID |
| Visual refresh default | `4` ticks per tool instance |

## Shared Overworld, Nether, and End progress

Configure every allowed Bukkit world and choose the persistence scope in `tools.yml`:

```yaml
tools:
  legendary_pickaxe:
    allowed_worlds:
      - Survival_World
      - Survival_World_nether
      - Survival_World_the_end
    progression:
      scope: PLAYER
      anchor_world: Survival_World
```

`PLAYER` means one owner/tool UUID, level, aggregate counter, SPECIFIC target map, activation state, and lifetime total across all listed worlds. The physical item uses the anchor as its persisted `bound_world`, but it is usable in every allowed world. `WORLD` keeps an independent record in each world.

The anchor must also appear in `allowed_worlds`. Names are compared case-insensitively, but administrators should copy the exact Bukkit world names used by the server. Common Multiverse-style names are only examples; PlexonTools does not infer a Nether or End name from the Overworld.

### Existing separate records

When an owner already has copies from multiple dimensions:

1. If at least one copy is bound to `progression.anchor_world`, the best anchor-world copy is canonical.
2. Otherwise, the highest-level and most-progressed copy is selected.
3. That selected UUID is rebound to the anchor without resetting its counters.
4. Other copies are deactivated and duplicate inventory items are replaced by the canonical item.

This intentionally treats the configured Overworld copy as authoritative instead of adding counters from multiple records, which would duplicate already-counted gameplay.

For compatibility, an omitted scope on a one-world definition behaves as `WORLD`. An omitted scope on a definition already listing two or more worlds behaves as `PLAYER`. Tools newly created through the admin GUI write an explicit `PLAYER` scope so adding dimensions later preserves one progression path.

## Block-event performance

PlexonTools already kept registry state in a concurrent memory cache and wrote SQLite updates asynchronously in coalesced transactions. Version 3.6.1 retains that design and its final shutdown drain; saving only at shutdown is not used because a crash would lose the entire session.

The optimized event path now:

- reads only the small identity portion of item PDC when a registry record exists;
- resolves current progress from the authoritative memory record;
- caches immutable per-definition requirement maps;
- updates progress and the dirty UUID immediately;
- retains only the newest pending item/action-bar visual for each instance;
- renders item metadata, lore, and action-bar output once per configured window.

Configure the window in `config.yml`:

```yaml
performance:
  progress-visual-refresh-ticks: 4
```

The accepted range is `1` through `20`. A value of `1` gives nearly immediate visuals; a larger value reduces metadata rendering and packets during rapid mining. Level-ups bypass the window, while quit, death, world transition, reload, and shutdown flush the latest visible state.

SQLite still writes through the asynchronous worker using `storage.flush-interval-ticks`, `write-batch-size`, and `max-pending-writes`. Bukkit inventory and world mutation remain on the server thread as required by the platform.

## GUI isolation

All PlexonTools top-inventory clicks and relevant drags are cancelled and denied at `LOWEST` priority. Back, previous, next, and level-move controls now use `SPECTRAL_ARROW` plus PlexonTools PDC action tags instead of a generic vanilla arrow.

For two ticks after PlexonTools owns an input, an `InventoryOpenEvent` guard allows only inventories backed by `PlexonGuiHolder`. This blocks GhostBlocks Remastered or another plugin from replacing the requested PlexonTools page because it matched the same clicked item.

## Upgrade checklist

1. Stop Paper and back up `plugins/PlexonTools`.
2. Replace the previous JAR with `PlexonTools-3.6.1.jar`.
3. Start Paper and confirm the existing database opens with schema `1`.
4. Compare live YAML with the refreshed files under `plugins/PlexonTools/examples`.
5. Add the exact dimension names and explicit `progression` section to each shared tool.
6. Enter each allowed dimension and confirm the same UUID, level, and current counters remain visible.
7. Re-test the `/pt gui` Back and page controls with GhostBlocks Remastered enabled.
8. Profile the same mining workload before and after; tune the visual window only if necessary.

No database migration or data deletion is required. Do not delete `plexontools.db`, and do not restore the historical `data.yml` over current state.
