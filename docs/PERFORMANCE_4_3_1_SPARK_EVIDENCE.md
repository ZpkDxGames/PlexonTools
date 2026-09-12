# PlexonTools 4.3.1 Spark server-profile evidence

Date: 2026-09-12

This evidence supplements `PERFORMANCE_4_3_1_SUSTAINED_MINING.md` with the real PlexonCraft Spark execution profile captured after the first 4.3.1 visual-renderer candidate.

## Profile metadata

- Paper: `26.2-121-a2a42c5`
- Minecraft: `26.2`
- Spark sampler engine: async, engine version `4.5`
- Sampling interval: `4 ms`
- Profile duration: `1401.664 s` (~23m21s)
- Server-thread ticks represented: `28,028`
- Players: `1`
- TPS: effectively `20.0`

## Whole-server MSPT

Spark platform statistics at the end of the capture:

- 1m mean: `3.570 ms`
- 1m median: `3.398 ms`
- 1m P95: `4.537 ms`
- 1m max: `25.021 ms`
- 5m mean: `3.420 ms`
- 5m median: `3.271 ms`
- 5m P95: `4.482 ms`
- 5m max: `113.710 ms`

The first ~15 one-minute windows form the clearest idle/control baseline. Their median-of-window-medians is approximately `2.186 ms` (mean `2.203 ms`).

The cleanest sustained-mining candidate segment is 18:45–18:47, where PlexonTools visual refresh samples are present and no PlexonTools reload occurs. Those one-minute window medians are:

- 18:45: `3.401 ms`, max `8.727 ms`
- 18:46: `3.167 ms`, max `7.088 ms`
- 18:47: `3.241 ms`, max `8.265 ms`

Mining median-of-medians: `3.241 ms`.

Delta from idle median-of-medians: approximately `+1.054 ms`.

This satisfies the campaign's one-player whole-server target of approximately idle + 2 ms median and idle + 5 ms P95/normal-tail behavior.

## PlexonTools attribution

The profile confirms the first candidate is active (`MessageService.deserializeCached` appears in the live stack).

The live visual-refresh stack is:

`ProgressionService.profiledVisualFlush`
→ `flushPendingVisuals`
→ `flushVisual`
→ `ToolItemService.refreshProgress`
→ `ToolItemService.renderLore`

Across the full 23m21s profile, Spark sampled approximately `152 ms` inside the coalesced PlexonTools visual flush. The current remaining renderer cost is concentrated in lore construction, especially `enchantmentLines`, `requirementLines`, progress placeholder generation, and MiniMessage escaping/parsing. There is no evidence of a large persistence or registry hotspot in the server profile.

Because the whole-server one-player MSPT acceptance target is already met, the proposed second same-level progress-only renderer is now **HOLD / evidence-triggered only** rather than an automatic follow-up. It should be implemented only if later 5/10-miner scaling or soak evidence shows the renderer becoming material again.

## Unrelated periodic spikes

The profile also exposes a separate recurring server spike approximately every five minutes. GUIPlus executes `ShutdownUtils.saveAllGuiData` / `saveScenes`, including YAML/SnakeYAML serialization and item NBT/SNBT capture, for roughly `108 ms` of sampled server-thread time per occurrence.

These runs line up with the repeating ~`115 ms` max-tick windows at approximately 18:29, 18:34, 18:39, and 18:44. This periodic spike is not attributable to sustained PlexonTools mining. The 18:44 window also contains an explicit PlexonTools reload, so that one window is not a clean benchmark.

PlexonPanel `TelemetryService.captureServer` is another visible background cost (about `3.6 s` sampled over the full profile), but it does not invalidate the PlexonTools one-player result.

## Gate state after this profile

- Root cause isolation: PASS — physical visual renderer selected and first fix implemented.
- First candidate CI: PASS.
- One-player internal `/pt perf`: materially improved.
- One-player whole-server Spark/MSPT: PASS.
- Maximum-level control: pending.
- 5-player scaling: pending.
- 10-player scaling: pending.
- Mixed-activity soak: pending.
- Merge / stable 4.3.1 publication: still blocked until remaining runtime gates are resolved.
