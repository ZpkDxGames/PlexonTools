# PlexonTools 4.3.1 sustained-mining performance audit

Status: **RUNTIME INVESTIGATION REQUIRED — NO 4.3.1 RELEASE CANDIDATE YET**

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

This symptom has **not yet been reproduced in this branch by an automated or connected PlexonCraft runtime session**. Runtime measurements below must be populated from real evidence before a performance implementation is accepted or `v4.3.1` is published.

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

The pending map prevents duplicate queued record IDs, but repeated progress while an instance is already dirty still pays revision/map/lock/accounting work. This is a confirmed **source-level candidate**, not yet a confirmed runtime root cause.

### Profiling caveat: dirty first/repeat counters add diagnostic lock traffic

While `/pt perf` is enabled, `ProgressionService.updateRegistry(...)` samples `InstanceRegistry.pendingWriteCount()` immediately before and after `InstanceRegistry.update(...)` to classify first dirty insert versus repeated dirty update. `pendingWriteCount()` itself synchronizes on `pendingLock`.

Therefore a profiled registry mutation currently adds two diagnostic `pendingLock` acquisitions around the actual mutation. Runtime analysis must not mistake this profiler overhead for production cost. Spark with `/pt perf` disabled is required alongside the stage profiler. If registry mutation is implicated, instrumentation should be refined before relying on very small timing deltas.

### GENERAL progression already has a partial allocation fast path

`RequirementProgression.advance(...)` avoids cloning/normalizing target maps for GENERAL requirements, but still returns a new `Result`; `ProgressionService` then creates a new `ToolState` for a changed result. Allocation remains a candidate only if an allocation profile or stage evidence shows it is material.

### Visual refresh remains a periodic synchronous candidate

The default progress visual cadence is four ticks. Progress visual work can still include item lookup, PDC/meta mutation, dynamic lore/progress rendering, MiniMessage work, inventory replacement and action-bar work. It must be isolated from per-block progression before changing cadence or renderer architecture.

### Public progress events remain an ecosystem boundary

Compatible progress events are batched. Runtime profiling must distinguish PlexonTools time before event dispatch from downstream listeners of `PlexonToolProgressEvent`. A downstream consumer regression must not be silently re-owned by PlexonTools.

## Current source-level bottleneck table

| Stage / subsystem | Source observation | Runtime status | Action before implementation |
| --- | --- | --- | --- |
| Active identity/context | Existing fast path and periodic revalidation | Unmeasured | Baseline/max-level comparison |
| Requirement progression | Per accepted unit; GENERAL avoids target-map clone | Unmeasured | `/pt perf isolate progression on` + allocation profile |
| Registry mutation | Per accepted unit | Unmeasured | `/pt perf isolate registry-mutation on` |
| Dirty bookkeeping | Revision/map/lock path entered per accepted unit | High-priority source candidate | Spark + registry isolation; account for profiler lock overhead |
| Natural provenance | In-memory warm path, async chunk loading | Unmeasured | `/pt perf isolate natural-tracking on` |
| Visual refresh | Four-tick coalesced synchronous work | High-priority periodic candidate | `visual-refresh`, `actionbar`, cadence controls |
| Abilities/drop handling | Mostly precomputed/fast when absent | Lower-priority candidate | `abilities` and `drop-abilities` isolation |
| Public progress events | Batched but may invoke external consumers | Ecosystem candidate | `progress-events` isolation + Spark call tree |

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

### Baseline evidence

Pending real PlexonCraft runtime capture.

| Metric | Idle | One normal miner | Max-level miner |
| --- | ---: | ---: | ---: |
| Median MSPT | PENDING | PENDING | PENDING |
| P95 MSPT | PENDING | PENDING | PENDING |
| P99 MSPT | PENDING | PENDING | PENDING |
| Max MSPT | PENDING | PENDING | PENDING |
| Block rate | n/a | PENDING | PENDING |

Spark baseline reference: **PENDING**

`/pt perf` baseline report: **PENDING**

## Isolation matrix

Use the same region, tool family, block stream and workload. Change one variable at a time. Reset all isolation switches after each test.

| Test | Control | Median MSPT | P95 MSPT | Interpretation |
| --- | --- | ---: | ---: | --- |
| A | equivalent maximum-level tool | PENDING | PENDING | Separates progression-side work from generic block path |
| B | `progression on` | PENDING | PENDING | Requirement/progression math |
| C | `registry-mutation on` | PENDING | PENDING | Runtime record + dirty bookkeeping |
| D | `natural-tracking on` | PENDING | PENDING | Provenance path |
| E | `visual-refresh on` | PENDING | PENDING | Physical metadata/lore refresh |
| F | `actionbar on` | PENDING | PENDING | Action-bar rendering/packet work |
| G | `abilities on` | PENDING | PENDING | Ability execution |
| H | `drop-abilities on` | PENDING | PENDING | Block-drop preparation/application |
| I | `progress-events on` | PENDING | PENDING | Local batching plus external event consumers |

Also compare:

- `progress-action-bar: false`;
- `progress-visual-refresh-ticks: 20`;
- normal configuration;
- equivalent max-level tool.

## Decision gate after baseline

No gameplay/performance implementation should be selected until the evidence identifies a dominant stage or allocation source.

- If max-level mining remains expensive, prioritize identity/provenance/abilities/drop handling/external block listeners/periodic tasks.
- If max-level mining returns near idle, prioritize progression, registry mutation, dirty bookkeeping, immutable state allocation and progress visuals.
- If `registry-mutation` isolation materially removes the regression, implement a race-safe clean → dirty generation model first.
- If `visual-refresh` isolation materially removes the repeating spikes, build a compiled/progress-only renderer before changing cadence.
- If `progress-events` isolation removes the regression, identify the downstream listener in Spark before changing PlexonTools event semantics.
- If no isolation removes the cost, inspect external event dispatch, Paper/block physics and allocation/GC pressure before modifying PlexonTools.

## Candidate implementation

**PENDING ROOT-CAUSE CONFIRMATION.**

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

- Feature-head CI: **PENDING IMPLEMENTATION**
- PR CI: **PENDING IMPLEMENTATION**
- Real runtime benchmark: **BLOCKING / PENDING**
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

Current evidence in this document is **source-level only** plus the original production symptom report. CI, synthetic benchmark and real PlexonCraft runtime evidence must be added as distinct sections and must never be represented as equivalent to one another.
