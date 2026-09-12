# PlexonTools 4.3.1 sustained-mining performance audit

Status: **BASELINE `/pt perf` CAPTURED — CONTROLLED ISOLATION STILL REQUIRED — NO 4.3.1 RELEASE CANDIDATE YET**

This document records the source-level audit and the mandatory PlexonCraft runtime gate for the sustained-mining regression reported against PlexonTools 4.3.0. It intentionally separates source evidence from runtime evidence. Nothing in this document is permission to infer a performance PASS from CI or source inspection.

## Release boundary

- Repository: `ZpkDxGames/PlexonTools`
- Baseline branch: `main`
- Exact baseline SHA: `819d4fa92ea5745b72ad5933424cb194ade673ca`
- Previous/current stable: `v4.3.0`
- Current stable artifact: `PlexonTools-4.3.0.jar`
- Intended compatible remediation version: `4.3.1`
- Runtime target: Paper `26.2`, Java `25`, PlexonCore `2.0.4`
- Rollback: stable `v4.3.0`

## Reported production symptom

The production report that opened this investigation is approximately:

| Workload | Observed server MSPT |
| --- | ---: |
| Idle / not continuously mining | ~2 ms |
| One player continuously mining with a PlexonTool | ~20 ms |

The exact ~2 → ~20 server-MSPT transition still requires a synchronized Paper/Spark capture. A real PlexonCraft `/pt perf` baseline has now been captured and is recorded below; it must not be confused with whole-server MSPT.

## Source-level evidence from baseline `main`

### Ordinary mining fast path already exists

`ToolProgressListener` retains an `ActiveToolContext` per player, periodically revalidates compact identity rather than fully decoding PDC every block, caches definition/state/ability information, and bypasses normal progression for maximum-level tools. This means another generic identity cache is not the default remediation.

### Progression still mutates authoritative state for each accepted block

For non-max-level progression, `ProgressionService.addProgressInternal(...)` still performs requirement advancement, materializes an updated immutable `ToolState`, updates registry state, refreshes the latest-state cache, batches the public progress event, and queues a visual refresh. Secondary work is coalesced, but the authoritative mutation path remains per accepted progress unit.

### Dirty persistence is coalesced at the write level but touched per mutation

`InstanceRegistry.update(...)` mutates the resident runtime record and then calls `markDirty(...)` for every accepted mutation. `markDirty(...)` currently:

1. increments a global revision;
2. updates `recordRevisions`;
3. acquires `pendingLock`;
4. performs `pendingWrites.putIfAbsent(...)` or snapshot bookkeeping;
5. calculates pending pressure/high-water state;
6. evaluates pressure-flush scheduling.

The pending map prevents duplicate queued record IDs, but repeated progress while an instance is already dirty still pays revision/map/lock/accounting work. This is a confirmed source-level candidate, not by itself a confirmed runtime root cause.

### Profiling caveat: dirty first/repeat counters add diagnostic lock traffic

While `/pt perf` is enabled, `ProgressionService.updateRegistry(...)` samples `InstanceRegistry.pendingWriteCount()` immediately before and after `InstanceRegistry.update(...)` to classify first dirty insert versus repeated dirty update. `pendingWriteCount()` itself synchronizes on `pendingLock`.

Those two diagnostic lock acquisitions are outside the timed `block.monitor.registry` stage, although they are inside the larger monitor/block path. Therefore `block.monitor.registry` measures the actual `InstanceRegistry.update(...)` call but not all profiler-side dirty classification overhead. Spark with `/pt perf` disabled remains required for production-like confirmation.

### GENERAL progression already has a partial allocation fast path

`RequirementProgression.advance(...)` avoids cloning/normalizing target maps for GENERAL requirements, but still returns a new `Result`; `ProgressionService` then creates a new `ToolState` for a changed result. Allocation remains a candidate only if an allocation profile or stage evidence shows it is material.

### Visual refresh remains a periodic synchronous candidate

The default progress visual cadence is four ticks. Progress visual work can still include item lookup, PDC/meta mutation, dynamic lore/progress rendering, MiniMessage work, inventory replacement and action-bar work.

The first real PlexonCraft profile strongly elevates this subsystem: actual visual execution is materially more expensive than registry mutation, requirement progression, natural provenance, event batching, action-bar rendering, and the ordinary block monitor path. Controlled `visual-refresh` isolation is now the next required test.

### Public progress events remain an ecosystem boundary

Compatible progress events are batched. Runtime profiling must distinguish PlexonTools time before event dispatch from downstream listeners of `PlexonToolProgressEvent`. A downstream consumer regression must not be silently re-owned by PlexonTools.

## Current source/runtime bottleneck table

| Stage / subsystem | Source observation | Runtime evidence so far | Next action |
| --- | --- | --- | --- |
| Active identity/context | Existing fast path and periodic revalidation | 97.36% context hit rate in initial sample | Keep as control; max-level test later |
| Requirement progression | Per accepted unit; GENERAL avoids target-map clone | avg 0.040 ms, P95 0.053 ms | Not primary based on first sample |
| Registry mutation | Per accepted unit | avg 0.009 ms, P95 0.014 ms | De-prioritized unless isolation/Spark contradicts |
| Dirty bookkeeping | Revision/map/lock path entered per accepted unit | 152 first / 1732 repeat; high repetition but timed registry cost small | Preserve as secondary optimization candidate only |
| Natural provenance | In-memory warm path | avg 0.014 ms, P95 0.019 ms | De-prioritized from first sample |
| Visual refresh | Four-tick coalesced synchronous work | `visual.total` avg 1.775 ms, P95 2.280 ms; item refresh avg 1.495 ms, P95 1.926 ms | **Run `visual-refresh` isolation next** |
| Action bar | Synchronous visual feedback | avg 0.205 ms, P95 0.273 ms | Test separately after visual-refresh isolation |
| Abilities/drop handling | Mostly precomputed/fast when absent | drop context avg 0.019 ms; drop total avg 0.042 ms | Lower priority |
| Public progress events | Batched but may invoke external consumers | event batching avg 0.013 ms; flush avg 0.012 ms | Lower priority locally; Spark still needed for consumers |

## Mandatory runtime reproduction environment

Record all of the following for baseline and candidate runs:

- exact Paper build;
- Java version;
- PlexonTools version and commit SHA;
- PlexonCore version;
- relevant loaded block/progression listeners/plugins;
- world and test region;
- block type/stream;
- tool ID, level, requirement mode and abilities;
- NATURAL / PLAYER_PLACED / UNKNOWN origin state;
- player count;
- view distance and simulation distance;
- idle baseline MSPT.

Keep `AREA_MINE_3X3` `DISABLED_SAFE` for the campaign.

## Baseline procedure — stable v4.3.0

1. Install the exact stable `PlexonTools-4.3.0.jar`.
2. Warm the exact test region for at least 30 seconds.
3. Capture idle Spark + Paper MSPT.
4. Run `/pt diagnostics`.
5. Run `/pt perf reset`.
6. Start a normal single-block sustained-mining workload with one player.
7. Capture a Spark profile with `/pt perf` **disabled** to observe production-like plugin/event cost.
8. Repeat the same workload with `/pt perf start 3000` and capture `/pt perf report console`.
9. Record TPS, median/P95/P99/max MSPT and block-break rate.

## Real PlexonCraft runtime evidence — initial `/pt perf` baseline

Captured from the user's live PlexonCraft mining workload on 2026-09-12. The screenshot was taken while the profiler still reported `RUNNING`, at 1935 block samples rather than a completed 3000-sample session. Treat this as a strong directional baseline, not the final benchmark table.

### Counters

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

Repeated-dirty mutations account for the large majority of registry mutations, confirming that the source-level dirty-coalescing opportunity is real. However, the measured registry stage itself is very small in this sample, so repeated dirty bookkeeping is not currently the leading runtime explanation.

### Timed stages

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

### Initial interpretation

The first live profile does **not** support selecting dirty persistence or requirement progression as the primary remediation yet.

- Registry mutation is two orders of magnitude below the heavy visual stages by average cost.
- Requirement progression is also comparatively small.
- Natural provenance and event batching are small in the measured local path.
- Action-bar generation is measurable but substantially cheaper than physical item refresh.
- `visual.item_refresh` dominates the visible rendering cost and reaches ~4.4 ms in the captured sample.
- The periodic visual task reaches ~5 ms max and is therefore capable of producing repeating synchronous spikes even though many task invocations are cheap/empty.

The strongest current hypothesis is physical progress item/meta/lore refresh. It must be confirmed by the controlled `visual-refresh` isolation before implementation.

### Whole-server evidence still missing

The screenshots do not provide synchronized Paper median/P95/P99/max MSPT or a Spark call tree. Therefore the original ~2 → ~20 server-MSPT report is not yet numerically reproduced in this audit.

| Metric | Idle | One normal miner | Max-level miner |
| --- | ---: | ---: | ---: |
| Median MSPT | PENDING | PENDING | PENDING |
| P95 MSPT | PENDING | PENDING | PENDING |
| P99 MSPT | PENDING | PENDING | PENDING |
| Max MSPT | PENDING | PENDING | PENDING |
| Block rate | n/a | PENDING | PENDING |

Spark baseline reference: **PENDING**

## Isolation matrix

Use the same region, tool family, block stream and workload. Change one variable at a time. Reset all isolation switches after each test.

| Test | Control | Median MSPT | P95 MSPT | Interpretation |
| --- | --- | ---: | ---: | --- |
| A | equivalent maximum-level tool | PENDING | PENDING | Separates progression-side work from generic block path |
| B | `progression on` | PENDING | PENDING | Requirement/progression math |
| C | `registry-mutation on` | PENDING | PENDING | Runtime record + dirty bookkeeping |
| D | `natural-tracking on` | PENDING | PENDING | Provenance path |
| E | `visual-refresh on` | **NEXT TEST** | **NEXT TEST** | Physical metadata/lore refresh |
| F | `actionbar on` | PENDING | PENDING | Action-bar rendering/packet work |
| G | `abilities on` | PENDING | PENDING | Ability execution |
| H | `drop-abilities on` | PENDING | PENDING | Block-drop preparation/application |
| I | `progress-events on` | PENDING | PENDING | Local batching plus external event consumers |

Also compare after the main isolation matrix:

- `progress-action-bar: false`;
- `progress-visual-refresh-ticks: 20`;
- normal configuration;
- equivalent max-level tool.

## Decision gate after baseline

The current evidence justifies prioritizing visual isolation, but not yet implementation.

- If `visual-refresh` isolation materially reduces the repeating cost, build a compiled/progress-only renderer before changing cadence.
- If `visual-refresh` does not materially improve the server profile, continue with max-level and other isolation controls rather than assuming source-level suspects are causal.
- If `actionbar` isolation alone materially improves the result, optimize action-bar generation independently.
- If `registry-mutation` later proves significant despite the first timings, implement a race-safe clean → dirty generation model.
- If `progress-events` isolation removes the regression, identify the downstream listener in Spark before changing PlexonTools event semantics.
- If no isolation removes the cost, inspect external event dispatch, Paper/block physics and allocation/GC pressure before modifying PlexonTools.

## Candidate implementation

**PENDING CONTROLLED VISUAL ISOLATION.**

The branch must prefer the smallest measured fix. No unrelated GUI, balance, feature, storage migration or Area Mine work belongs in 4.3.1.

## Correctness requirements for any implementation

Any accepted implementation must preserve:

- every accepted block contributes exactly once;
- cancelled/wrong-tool/invalid-world/owner-invalid progress remains zero;
- exact GENERAL and SPECIFIC progression math;
- exact level boundary and excess-progress behavior;
- no lost update during an in-flight persistence write;
- exact final persisted progress after shutdown/restart;
- tool PDC identity and existing tool compatibility;
- correct full refresh on level/form/material/enchantment transitions;
- exact public event total and level-up ordering;
- NATURAL accepted, PLAYER_PLACED rejected and UNKNOWN fail-closed;
- no synchronous SQLite/file access in ordinary mining;
- no Bukkit/Paper live objects used asynchronously.

## Acceptance targets

For the same machine, configuration, region, tool and block stream:

- eliminate sustained PlexonTools-attributed ~15–20 MSPT behavior for one ordinary miner;
- target median MSPT no more than approximately idle + 2 ms;
- target P95 no more than approximately idle + 5 ms;
- no repeating PlexonTools synchronous spike above 10 ms without an identified exceptional operation;
- ordinary PlexonTools `BlockBreakEvent` P95 comfortably sub-millisecond where practical;
- max-level path near identity/ability/provenance-only cost;
- predictable 1/5/10 miner scaling without unbounded queue/task/context growth;
- at least 30 minutes mixed-activity soak before stable publication.

These are engineering targets, not evidence to be backfilled or inferred.

## CI and release state

- Documentation-head CI: **PASS** — PR #14 run `34716144484`
- Runtime implementation: **NOT SELECTED YET**
- Real runtime baseline: **PARTIAL `/pt perf` CAPTURED**
- Controlled isolation matrix: **IN PROGRESS**
- Spark / whole-server MSPT gate: **BLOCKING / PENDING**
- Merge to `main`: **NOT PERMITTED YET**
- Stable `v4.3.1`: **NOT PERMITTED YET**

The final JAR must be built from the exact merged `main` commit only after runtime evidence passes. Required release assets remain:

- `PlexonTools-4.3.1.jar`
- `SHA256SUMS.txt`
- `TEST_SUMMARY.txt`
- `PROVENANCE.txt`

## Rollback

Until 4.3.1 is runtime-certified and published, `v4.3.0` remains the authoritative stable release. If a future 4.3.1 candidate fails correctness or performance gates, restore the exact `v4.3.0` JAR and preserve the database; do not regenerate player tools.

## Evidence classification

Current evidence now consists of:

1. source-level audit evidence;
2. green documentation-branch CI evidence;
3. real PlexonCraft `/pt perf` stage/counter evidence from a 1935-sample in-progress baseline;
4. the original production whole-server MSPT symptom report.

Synthetic benchmark, synchronized Spark, completed whole-server MSPT distributions, isolation results, post-fix scaling and soak evidence remain pending and must not be inferred from the sources above.
