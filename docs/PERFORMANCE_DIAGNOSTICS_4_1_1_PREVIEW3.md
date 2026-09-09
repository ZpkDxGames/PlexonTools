# PlexonTools 4.1.1 Preview 3 — Performance Diagnostics

Preview 3 is a diagnostic prerelease for ordinary single-block mining. It does not declare a root cause until live profiling data identifies one.

## Safety

The profiler is disabled by default. All isolation switches are development-only and reset when profiling stops, PlexonTools reloads, or the server restarts.

Isolation changes normal gameplay semantics. Use it only for short, controlled benchmark runs. Stable `v4.1.1` remains blocked until live validation shows a material improvement.

## Commands

```text
/pt perf start
/pt perf start 1000
/pt perf stop
/pt perf reset
/pt perf status
/pt perf report
/pt perf report console
/pt perf isolate <stage> <on|off>
```

All commands require `plexontools.admin`.

Supported isolation stages:

```text
progression
natural-tracking
registry-mutation
visual-refresh
actionbar
abilities
drop-abilities
progress-events
```

## Recorded stages

The profiler records the high-priority and monitor portions of `BlockBreakEvent`, compact identity/context resolution, validation, progression requirement math, registry mutation, natural-block provenance, event batching, visual queueing, drop preparation, ability handling, `BlockDropItemEvent`, item visual refresh, action-bar generation, and the scheduled visual/event/passive-effect tasks.

Each timing stage retains bounded samples and reports average, maximum, P50, P95, and P99 values. The report also retains worst-sample queue metadata so a periodic burst can be correlated with pending visuals, pending event groups, dirty instances, and active tool contexts.

Operation counters include context hits/misses, PDC identity reads, UUID parses, definition lookups, registry reads/mutations, requirement progression calls, natural-block lookups/consumes, dirty queue first/repeated writes, progress-batch create/merge counts, visual refreshes, inventory scans, and BlockDrop fallback resolutions.

## Recommended live sequence

Use the same player, tool, block type, world/region, mining duration, view distance, and plugin set. Keep 3x3 Area Mine disabled.

1. Warm up for 20–60 seconds.
2. `/pt perf start 1000` and perform ordinary mining with the full pipeline.
3. `/pt perf report console`.
4. Repeat with a maximum-level legendary tool.
5. Repeat with abilities disabled through the diagnostic isolation switch.
6. Repeat natural tracking ON/OFF.
7. Repeat visual refresh and action bar ON/OFF.
8. Compare the high-percentile and maximum values, not only averages.

For each run, also capture Spark over a sufficiently long interval to include the recurring MSPT pattern.

## Interpretation

A high steady-state context hit rate with low PDC/UUID counts indicates the Preview 2 active-context fast path is functioning. A max-level tool should remove requirement/progression work. If max-level mining remains expensive, focus on recognition/validation, natural tracking, ability/drop handling, or periodic visual/event work.

Large `task.visual_refresh` or `task.progress_event_flush` maxima with elevated pending queue counts indicate burst work rather than individual `BlockBreakEvent` cost. Large `visual.locate` values or inventory-scan counts indicate item-location fallback. Large `drop.total` with BlockDrop fallbacks indicates the prepared drop context is not being reused reliably.

Do not turn diagnostic isolation behavior into a permanent optimization unless the corresponding stage is measured as a material bottleneck and gameplay semantics can be preserved.
