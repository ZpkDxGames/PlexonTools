# PlexonTools 4.3.1 sustained-mining performance audit

Status: **ROOT CAUSE ISOLATED — FIRST VISUAL-RENDERER REMEDIATION IMPLEMENTED — CANDIDATE RUNTIME GATE PENDING**

This document is the authoritative performance audit for the PlexonTools 4.3.1 sustained-mining remediation. Source inspection, CI, `/pt perf`, Spark, whole-server MSPT and soak evidence are intentionally kept distinct. A green build is not a runtime performance PASS.

## Release boundary

- Repository: `ZpkDxGames/PlexonTools`
- Baseline branch: `main`
- Exact baseline SHA: `819d4fa92ea5745b72ad5933424cb194ade673ca`
- Stable baseline: `v4.3.0`
- Stable artifact: `PlexonTools-4.3.0.jar`
- Runtime target: Paper `26.2`, Java `25`, PlexonCore `2.0.4`
- Intended compatible remediation: `4.3.1`
- Remediation branch: `perf/4.3.1-sustained-mining-mspt`
- Rollback: exact stable `v4.3.0`

## Production symptom

PlexonCraft was reported at approximately:

| Workload | Server MSPT |
| --- | ---: |
| Idle / no sustained mining | ~2 ms |
| One player continuously mining with a PlexonTool | ~20 ms |

This whole-server symptom remains the final operational gate. The plugin-local profiler evidence below identifies the dominant PlexonTools subsystem but must not be represented as Paper-wide MSPT.

## Baseline source audit

### Existing fast path preserved

The 4.3.0 ordinary mining architecture already has:

- cached active-tool context;
- periodic compact identity revalidation instead of full PDC parsing every block;
- cached definition/state/ability information;
- max-level progression bypass;
- asynchronous/coalesced SQLite persistence;
- memory-only ordinary natural-block provenance decisions;
- batched progress events;
- coalesced visual refreshes;
- Area Mine `DISABLED_SAFE`.

The remediation therefore does not replace that architecture or add another generic block cache.

### Progression and persistence remain per accepted mutation, but are not the measured primary cost

Every accepted non-max progress unit still enters requirement progression, immutable `ToolState` creation, registry mutation, latest-state update, event aggregation and visual queueing.

`InstanceRegistry.markDirty(...)` also still performs revision/map/lock bookkeeping on repeated mutations even when an instance is already queued. This remains a valid secondary optimization opportunity, but runtime evidence below does not justify changing its race-sensitive persistence model first.

### Profiler caveat

While `/pt perf` is enabled, dirty first/repeat classification calls `pendingWriteCount()` before and after registry update. Those calls acquire `pendingLock` and are outside the timed `block.monitor.registry` stage. Spark with `/pt perf` disabled remains required for production-like confirmation, but the measured registry stage is sufficiently small that this caveat does not explain the visual-isolation result.

## Real PlexonCraft baseline — stable v4.3.0

Captured 2026-09-12 during live sustained single-block mining. The screenshot was taken while the session was still running at 1935 samples, so this is a strong directional baseline rather than the final 3000-sample release benchmark.

### Baseline counters

| Counter | Value |
| --- | ---: |
| Samples | 1935 |
| Context hit rate | 97.36% (1884 / 1935) |
| PDC reads | 19620 |
| UUID parses | 9810 |
| Registry reads | 0 |
| Registry mutations | 1884 |
| Inventory scans | 0 |
| Drop fallbacks | 1935 |
| Natural lookups | 1884 |
| Requirement calls | 1884 |
| Dirty first / repeat | 152 / 1732 |
| Event groups created / merged | 1155 / 728 |

### Baseline timed stages

| Stage | Avg ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: |
| `block.total` | 1.125 | 1.565 | 1.865 | 4.951 |
| `task.visual_refresh` | 0.515 | 1.947 | 2.345 | 5.005 |
| `visual.total` | 1.775 | 2.280 | 2.537 | 4.867 |
| `visual.item_refresh` | 1.495 | 1.926 | 2.139 | 4.395 |
| `block.monitor.total` | 0.122 | 0.177 | 0.231 | 3.719 |
| `visual.actionbar` | 0.205 | 0.273 | 0.374 | 0.666 |
| `drop.total` | 0.042 | 0.065 | 0.094 | 0.241 |
| `block.monitor.requirement` | 0.040 | 0.053 | 0.087 | 0.218 |
| `drop.context` | 0.019 | 0.044 | 0.072 | 0.238 |
| `task.progress_event_flush` | 0.012 | 0.040 | 0.052 | 0.701 |
| `block.high.total` | 0.026 | 0.070 | 0.115 | 0.288 |
| `visual.locate` | 0.008 | 0.083 | 0.110 | 0.401 |
| `block.monitor.natural` | 0.014 | 0.019 | 0.048 | 0.076 |
| `block.monitor.event_batch` | 0.013 | 0.019 | 0.049 | 0.274 |
| `block.monitor.registry` | 0.009 | 0.014 | 0.032 | 0.121 |

The baseline strongly points at physical item/lore rendering: `visual.item_refresh` alone is roughly 1.5 ms average and the periodic visual task reaches ~5 ms, while registry, provenance, event aggregation and progression math are all much smaller.

## Isolation matrix

Use the same region, tool family and block stream and change one variable at a time.

| Test | Control | Result | Interpretation |
| --- | --- | --- | --- |
| A | equivalent maximum-level tool | PENDING | Generic block path vs progression |
| B | `progression on` | PENDING | Requirement/progression math |
| C | `registry-mutation on` | PENDING | Runtime record + dirty bookkeeping |
| D | `natural-tracking on` | PENDING | Provenance |
| E | `visual-refresh on` | **ROOT-CAUSE ISOLATION PASS** | Physical item/lore visual path is dominant |
| F | `actionbar on` | Optional follow-up | Action bar is secondary in baseline |
| G | `abilities on` | PENDING if needed | Ability execution |
| H | `drop-abilities on` | PENDING if needed | Drop preparation/application |
| I | `progress-events on` | PENDING if needed | Local batching / downstream listeners |

## Test E — visual refresh isolated

Captured 2026-09-12 on live PlexonCraft with `DIAGNOSTIC ISOLATION ACTIVE: visual-refresh`. The report was still running at 2019 samples.

### Isolation counters

| Counter | Value |
| --- | ---: |
| Samples | 2019 |
| Context hit rate | 97.67% (1972 / 2019) |
| PDC reads | 17398 |
| UUID parses | 8699 |
| Registry reads | 0 |
| Registry mutations | 1928 |
| Inventory scans | 0 |
| Drop fallbacks | 2019 |
| Natural lookups | 1928 |
| Requirement calls | 1928 |
| Dirty first / repeat | 137 / 1791 |
| Event groups created / merged | 1143 / 784 |

### Isolation timed stages

| Stage | Avg ms | P95 ms | P99 ms | Max ms |
| --- | ---: | ---: | ---: | ---: |
| `block.total` | 0.049 | 0.063 | 0.103 | 5.290 |
| `block.monitor.requirement` | 0.038 | 0.047 | 0.064 | 0.107 |
| `drop.total` | 0.029 | 0.040 | 0.071 | 0.129 |
| `drop.context` | 0.013 | 0.029 | 0.053 | 0.125 |
| `task.progress_event_flush` | 0.030 | 0.075 | 0.100 | 0.591 |
| `block.high.total` | 0.018 | 0.048 | 0.081 | 0.168 |
| `block.monitor.event_batch` | 0.010 | 0.012 | 0.024 | 0.072 |
| `block.monitor.natural` | 0.010 | 0.010 | 0.024 | 0.346 |
| `block.monitor.registry` | 0.007 | 0.007 | 0.020 | 0.501 |
| `block.monitor.state_mutation` | 0.003 | 0.006 | 0.009 | 0.031 |
| `block.high.validation` | 0.002 | 0.003 | 0.005 | 0.056 |
| `block.monitor.target` | 0.005 | 0.005 | 0.011 | 0.057 |
| `block.high.identity` | 0.039 | 0.060 | 0.094 | 0.162 |
| `block.high.context` | 0.003 | 0.004 | 0.004 | 0.053 |

The isolated run reduces `block.total` from:

- **1.125 → 0.049 ms average** (~95.6% lower);
- **1.565 → 0.063 ms P95** (~96.0% lower);
- **1.865 → 0.103 ms P99** (~94.5% lower).

A single ~5.29 ms maximum outlier remains, but the sustained distribution collapses when visual refresh is disabled. Registry mutation remains ~0.007 ms average in this control. This is sufficient evidence to select the visual item/lore renderer as the first remediation target under the directive's decision tree.

## Confirmed root cause

The dominant PlexonTools-local sustained-mining cost is the coalesced **physical progress item/lore refresh path**, not ordinary progression math or SQLite dirty bookkeeping.

The current `ToolItemService.refreshProgress(...)` still rebuilds presentation repeatedly, including placeholder rendering and repeated MiniMessage deserialization. Static lore rows, enchantment presentation, owner/profile text and quantized progress output can therefore be reparsed many times during sustained mining.

## First candidate implementation

Selected strategy: **bounded immutable rendered-component cache**.

Candidate head after implementation/tests: `e49f932f7fff8392f2af18adafe2eca5ca65b1a7` (subject to later documentation commits).

Implementation characteristics:

- `MessageService` caches fully rendered, normalized MiniMessage strings to immutable Adventure `Component` objects;
- cache is access-ordered and bounded to 4096 entries;
- identical static lore rows avoid repeated MiniMessage deserialization;
- quantized outputs such as percentage/progress-bar rows can also hit the cache while their rendered value is unchanged;
- genuinely changing exact progress rows still deserialize normally;
- cache is cleared on message reload;
- synchronization prevents unsafe concurrent map access without moving Bukkit work off-thread;
- no live Bukkit objects enter background work;
- no gameplay/progression/PDC/API/event/storage semantics change.

A regression test verifies that identical rendered output reuses the same immutable component and that the cache remains bounded.

This is deliberately the **smallest measured fix**. Do not yet combine it with progress-only PDC writes, lore-layout compilation, cadence changes or persistence changes. Re-measure this candidate first.

## Candidate runtime test

After candidate CI passes, run the candidate JAR under the same workload with all diagnostic isolation switches OFF.

Capture:

1. `/pt perf reset`;
2. `/pt perf start 3000`;
3. sustained single-block mining in the same test region;
4. `/pt perf report console` after a useful sample;
5. Paper server MSPT during the same workload;
6. Spark with `/pt perf` disabled over a comparable workload.

Primary candidate comparison:

| Metric | Stable 4.3.0 baseline | Candidate | Gate |
| --- | ---: | ---: | --- |
| `block.total` avg | 1.125 ms | PENDING | materially lower |
| `block.total` P95 | 1.565 ms | PENDING | materially lower |
| `visual.item_refresh` avg | 1.495 ms | PENDING | substantially lower |
| `visual.item_refresh` P95 | 1.926 ms | PENDING | substantially lower |
| Paper median MSPT | PENDING | PENDING | ~idle + 2 ms target |
| Paper P95 MSPT | PENDING | PENDING | ~idle + 5 ms target |

If rendered-component caching is insufficient, the next measured optimization is a true progress-only physical item renderer: update only mutable progression PDC, cache stable instance/profile placeholders, reuse static lore components, and avoid display-name/static metadata reconstruction during same-level progress refreshes.

## Correctness invariants

Any candidate and final 4.3.1 release must preserve:

- exactly-once accepted progress;
- zero progress for cancelled/wrong-tool/owner/world-invalid actions;
- exact GENERAL and SPECIFIC math and level boundaries;
- no lost persistence update during an in-flight async flush;
- shutdown/restart exact final state;
- existing tool PDC identity and Legendary Tool compatibility;
- immediate full metadata/profile refresh on level/form/material/enchantment transitions;
- exact public progress totals and level-up ordering;
- NATURAL accepted, PLAYER_PLACED rejected, UNKNOWN fail-closed;
- no synchronous SQLite/file I/O on the ordinary mining path;
- Area Mine remains `DISABLED_SAFE`.

## Acceptance gates

For the same host/configuration/region/tool/block stream:

- no sustained PlexonTools-attributed ~15–20 MSPT behavior for one miner;
- target Paper median no more than approximately idle + 2 ms;
- target Paper P95 no more than approximately idle + 5 ms;
- no repeating PlexonTools synchronous spike above 10 ms without an identified exceptional operation;
- ordinary PlexonTools block path P95 comfortably sub-millisecond where practical;
- predictable 1/5/10 miner scaling;
- no unbounded queue/task/context/cache growth;
- at least 30 minutes mixed-activity soak;
- no lost progress, stale visuals or lost database state.

## Spark evidence

Still required before stable publication:

1. stable v4.3.0 ordinary mining;
2. optimized candidate ordinary mining;
3. optimized candidate maximum-level mining;
4. candidate with five miners if practical.

Spark must distinguish PlexonTools frames, event dispatch/external progress-event consumers, item/lore/MiniMessage work, inventory/meta work, persistence synchronization, provenance and ability/drop frames.

## CI and release state

- Stable baseline: `v4.3.0` / `819d4fa92ea5745b72ad5933424cb194ade673ca`
- Documentation CI before implementation: PASS, run `34716144484`
- Visual isolation Test E: **PASS / root cause isolated**
- First implementation candidate CI: **RUNNING** at time of this update
- Candidate runtime benchmark: **PENDING**
- Spark comparison: **PENDING**
- 1/5/10 miner scaling: **PENDING**
- 30-minute soak: **PENDING**
- Merge to `main`: **NOT PERMITTED YET**
- Stable `v4.3.1`: **NOT PERMITTED YET**

The final release JAR must be built from the exact merged `main` commit after all runtime gates pass. Required final assets:

- `PlexonTools-4.3.1.jar`
- `SHA256SUMS.txt`
- `TEST_SUMMARY.txt`
- `PROVENANCE.txt`

## Rollback

Until 4.3.1 is runtime-certified and published, restore the exact `v4.3.0` JAR if a candidate fails. Preserve `plexontools.db`; existing player tools do not need regeneration.
