# PlexonTools 4.2.0 Performance & Reliability Audit

> Implementation branch: `agent/4.2.0-performance-reliability`  
> Baseline: `main` at `115820eb2a1ad774ab43d38c8f872c1614f5ce51` (`4.1.1`)  
> Runtime target: Paper 26.2 / Java 25  
> Status: implementation candidate; **not a stable-release declaration**

## Purpose

This document records the source-level audit and the first implementation slice for the PlexonTools 4.2.0 performance/reliability release. It deliberately separates code/CI verification from runtime performance claims. Spark comparisons, concurrent-player stress tests, and soak tests must still be run on a representative Paper server before `v4.2.0` can be called stable.

## Baseline architecture preserved

The 4.1.1 architecture already contains substantial performance work and remains the foundation of 4.2.0:

- authoritative in-memory progression state;
- asynchronous, coalesced SQLite persistence;
- WAL-backed storage and bounded write batches;
- in-memory natural/player-placed provenance;
- coalesced visual refreshes;
- two-tick public progress-event batching;
- immediate level-up events;
- reusable active-tool context for ordinary mining;
- reusable BlockBreak-to-BlockDrop ability context;
- optional PlexonCore integration with standalone fallback;
- `/pt perf` diagnostics/profiling;
- reproducible JAR integrity verification.

No public API class, public event class, database schema, or progression semantic is intentionally changed by this implementation slice.

## Source audit findings

### 1. Passive holder effects still performed whole-server scans

`AbilityService` refreshed passive holder effects by iterating every online player every 40 ticks, then re-resolving item identity and ability state for everyone. The cost therefore scaled with total online population rather than only players relevant to PlexonTools passive effects.

**4.2 action:** maintain event-driven dirty/holder sets. The periodic refresh now iterates only known passive holders, while inventory/held-slot/lifecycle events mark players for reconciliation. The task is not started at all if no enabled tool definition contains a HOLDER potion ability.

### 2. BlockDrop ability context could be overwritten

The 4.1.1 drop context was effectively one pending entry per player. Multiple relevant breaks by the same player before the matching `BlockDropItemEvent` could overwrite the earlier Auto Smelt/Magnet context.

**4.2 action:** correlate by player + exact world/block position, bound the number of pending contexts per player, enforce a short age window, and clean state on world/death/quit lifecycle events.

### 3. Ordinary mining still had avoidable profiler/context work

The normal block path still performed duplicate profiler timestamp setup and an unconditional profiler-only map lookup in the MONITOR phase. Max-level tools also continued into target/provenance checks even though progression could no longer advance.

**4.2 action:**

- reuse one HIGH-stage profiler timestamp;
- keep the profiler-only event timer map empty while profiling is disabled and skip lookup when empty;
- reject max-level progression before target/provenance/mutation work while retaining active abilities;
- cache stable world authorization data in the active context;
- retain event-driven invalidation plus the existing periodic identity safety revalidation.

### 4. Area Mine had no explicit hard-budget service boundary

The existing 3x3 implementation could dispatch a secondary `BlockBreakEvent` for every extra block. That is the safest compatibility behavior, but it lacked a dedicated coordinator and explicit future-proof bounds.

**4.2 action:** introduce `BulkBreakCoordinator`.

Current effective mode is `STRICT_EVENTS`:

- secondary blocks remain compatible Bukkit `BlockBreakEvent`s;
- cancellation/protection semantics remain authoritative;
- candidate positions are deduplicated;
- maximum secondary blocks per activation are bounded;
- maximum dispatched blocks per player per tick are bounded;
- per-player budget state is cleaned on lifecycle transitions;
- runtime totals/caps are visible in diagnostics.

`OPTIMIZED` is recognized as a requested mode but deliberately falls back to `STRICT_EVENTS` until explicit protection/integration hooks can guarantee equivalent safety. No protection bypass is accepted as a performance optimization.

### 5. Natural provenance lacked bulk primitives and pressure snapshots

The provenance system was already memory-first and did not query SQLite during block breaks. However, there was no bulk consume API for a future optimized Area Mine path and limited operator visibility into loaded/unknown chunks.

**4.2 action:** add batch consumption primitives and cheap snapshot diagnostics for:

- loaded provenance chunks;
- unknown/fail-closed chunks;
- tracked placed positions;
- pending load work;
- load batches;
- retries;
- failed loads.

The strict Area Mine path continues to let normal Bukkit events drive provenance semantics; the batch API is an internal primitive for future safe bulk processing.

### 6. Reload did not refresh AbilityService runtime caches/tasks

Tool/config reload refreshed definitions but did not restart the ability service. Passive-holder applicability, resolved potion metadata, and new bulk runtime settings could therefore remain stale after `/pt reload`.

**4.2 action:** restart the ability runtime once after a successful definition/config reload and clear its lifecycle caches/budgets.

## New operator controls

Bundled `config.yml` now documents:

```yaml
performance:
  progress-visual-refresh-ticks: 4

  area-mine:
    mode: STRICT_EVENTS
    max-secondary-blocks: 8
    max-blocks-per-player-per-tick: 8
```

These are bundled defaults/reference values. Existing live administrator configuration is not overwritten merely to update the version header or introduce these keys; missing keys use safe defaults.

## Diagnostics additions

`/pt diagnostics` now includes cheap snapshot information for:

- loaded/unknown provenance chunks;
- tracked player-placed positions;
- pending provenance loads;
- provenance load batches/retries/failures;
- active passive-effect holders;
- pending correlated block-drop contexts;
- effective Area Mine compatibility mode;
- Area Mine activation/tick caps;
- Area Mine accepted/dispatched totals and budget-limited activations.

These values do not require `/pt perf` to be actively sampling.

## Tests added/expanded

Automated coverage in this slice includes:

- provenance batch-consume ordering;
- placed-to-natural consumption behavior;
- provenance loaded/unknown/tracked-position snapshot counts;
- unload cleanup of provenance state;
- fail-safe Area Mine compatibility-mode parsing.

Existing 4.1.1 tests continue to cover progression/event batching/storage/API behavior.

## CI verification

The branch has passed the repository Build workflow using Java 25, including:

- clean Gradle test/package stage;
- `check`/JAR integrity verification;
- required public API/event classes;
- bundled SQLite JDBC/native entries;
- no shaded PlexonCore runtime classes;
- distribution verification;
- whitespace verification;
- verified artifact upload.

Passing CI proves build/test/package integrity only. It does **not** prove runtime performance acceptance gates.

## Runtime validation still required

Before a stable `v4.2.0` release, run equivalent 4.1.1 and 4.2.0 profiles in the same environment and record the environment plus `/pt perf` output.

Required scenarios remain:

1. idle/no active PlexonTools progression;
2. one rapid miner;
3. five concurrent miners;
4. ten concurrent miners;
5. visual pressure (action bar + lore enabled);
6. natural-only progression with placed decoys;
7. Area Mine staging using `STRICT_EVENTS`;
8. at least a 30-minute mixed-activity soak.

The runtime gate should verify, at minimum:

- no material ordinary-mining P95/P99 regression against 4.1.1;
- no new synchronous database/file frames on gameplay events;
- no repeating unexplained >5 ms PlexonTools synchronous spike;
- persistence/provenance queues recover after load;
- no monotonic lifecycle collection growth;
- exact public progress totals and level boundaries;
- no natural-block, owner, world, or protection bypass;
- predictable scaling at five and ten simultaneous users.

## Intentionally not claimed yet

This branch does **not** claim:

- a percentage performance improvement;
- completion of the ten-player gate;
- completion of the 30-minute soak gate;
- protection-safe `OPTIMIZED` Area Mine mode;
- stable `v4.2.0` readiness.

Those statements require runtime evidence, not source inspection.

## Next implementation work

The remaining 4.2 work should prioritize measured bottlenecks and reliability evidence, especially:

- persistence rate/high-water/commit metrics and failure-pressure reporting;
- stronger bulk provenance write coalescing where measurements justify it;
- additional lifecycle and overlapping BlockBreak/BlockDrop tests;
- visual unchanged-refresh accounting and inventory fallback metrics;
- runtime rolling-rate metrics for `/pt diagnostics` / `/pt perf`;
- full thread-ownership/lifecycle audit documentation;
- controlled Spark comparison and stress/soak evidence;
- final README/CHANGELOG/release notes/version bump only after candidate validation.

## Stable release rule

Do not tag or publish `v4.2.0` until the manual runtime gates are recorded and the release checklist is complete. The objective is a production-grade PlexonTools release that remains correct and bounded under sustained concurrent use, not an unverified benchmark claim.
