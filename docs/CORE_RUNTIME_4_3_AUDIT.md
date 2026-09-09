# PlexonTools 4.3.0 — Core Runtime Source Audit

Status: **CANDIDATE AUDIT — stable runtime authority is not yet approved**

Baseline: PlexonTools 4.2.0 (`fa1e60f117135cc91c53f7582af9b05b6e3d85a1`)

Core inspected: released PlexonCore 2.0.0 (`v2.0.0`, target `ac3c91a88d14c1efd00de855ac75112f3fd657b7`)

## Current listener ownership

| Listener method | Event | Frequency | 4.2 role | 4.3 candidate status |
|---|---|---:|---|---|
| `ToolProgressListener#onBlockBreak` | `BlockBreakEvent` HIGH | very high | active-tool identity, authorization, EXP booster | **LOCAL — retained** |
| `ToolProgressListener#onBlockBreakAbilities` | `BlockBreakEvent` MONITOR | very high | final cancellation check, provenance, progression, drop context, Area Mine | **LOCAL — retained** |
| `AbilityService` drop listener | `BlockDropItemEvent` | high | Auto Smelt/Magnet correlation | **LOCAL — retained** |
| `ToolProgressListener#onBlockPlace` | `BlockPlaceEvent` | medium | placement progression | LOCAL |
| `ToolProgressListener#onEntityDeath` | `EntityDeathEvent` | medium | kill progression/abilities | LOCAL |
| `ToolProgressListener#onAttack` | `EntityDamageByEntityEvent` HIGH | high | attack validation/context | LOCAL |
| `ToolProgressListener#onDamageResolved` | `EntityDamageByEntityEvent` MONITOR | high | final damage progression | LOCAL |
| `ToolProgressListener#onFish` | `PlayerFishEvent` | low | fishing progression/abilities | LOCAL |
| `ToolProgressListener#onHarvest` | `PlayerHarvestBlockEvent` | medium | farming progression | LOCAL |
| inventory/held/world/death/quit invalidators | several | medium | active-context invalidation | LOCAL |
| `NaturalBlockTracker` provenance listeners | place/break/piston/explosion/falling/chunk events | mixed | anti-exploit provenance | **LOCAL — authoritative** |

## Released Core 2 contract observed

The released Core 2 runtime is exposed directly through `PlexonCoreAPI.events()` and `PlexonCoreAPI.blockOrigins()`. There is no separate `PlexonRuntimeAPI` type in the released source.

The Core block gateway currently:

1. listens to `BlockBreakEvent` at `HIGHEST`;
2. builds immutable player/world/block facts;
3. optionally resolves Core origin;
4. optionally inspects the main-hand PDC namespace;
5. dispatches the immutable context to subscribers.

This is useful infrastructure, but it is not yet sufficient to replace the complete PlexonTools 4.2 mining path safely.

## Gate finding A — mutable phase ordering

PlexonTools modifies block EXP at `HIGH`. Core 2 acquires/dispatches its shared block context at `HIGHEST`.

Bukkit event order means `HIGH` runs before `HIGHEST`. Therefore the released Core context does not exist early enough to replace the identity acquisition used by the current PlexonTools mutable EXP phase.

Moving the EXP mutation to a later phase or relying on same-priority registration order would change a proven 4.2 contract and is not accepted without an explicit Core phase contract and runtime tests.

## Gate finding B — final cancellation outcome

Core 2 exposes a single `HIGHEST` acquisition/dispatch phase. PlexonTools grants progression at `MONITOR` only after checking the final cancellation state.

The Core context contains no final outcome/cancelled flag and no event reference. A Core callback alone therefore cannot be the authoritative progression trigger without a second/final outcome primitive or a local MONITOR correlation layer.

The 4.3 candidate keeps the local MONITOR authority.

## Gate finding C — existing provenance migration

PlexonTools already owns persisted player-placed provenance from production worlds. Core 2 maintains a separate `core_block_origin` database and has no released import API for PlexonTools provenance.

On an upgraded server, a Core chunk loaded successfully from an empty Core origin database may be classified `NATURAL` even when PlexonTools' legacy database knows a position was player placed. Switching authority without import/shadow parity would create an anti-exploit regression.

## Gate finding D — falling blocks

PlexonTools 4.2 explicitly tracks `FallingBlock` landing/drop transitions using `EntityChangeBlockEvent` and `EntityDropItemEvent`.

Released Core 2 origin tracking covers place, break/removal, burn/fade, explosions, pistons and chunk lifecycle, but does not include equivalent falling-block provenance.

Local provenance remains authoritative until Core gains equivalent behavior or a safe hybrid provider is proven.

## Gate finding E — item identity cost

The released Core identity resolver enumerates PDC keys in every requested namespace for every routed break requiring identity.

PlexonTools 4.2 already retains `ActiveToolContext` and normally revalidates compact identity only every ten ticks. Enabling Core identity on every ordinary break could therefore increase ItemMeta/PDC work for a single migrated module.

No stable authority switch is allowed until A/B profiling proves Core mode is at least performance-neutral and shared-module benefit is measured.

## Candidate action completed

The 4.3 candidate now:

- compiles against the exact PlexonCore 2.0.0 release artifact;
- verifies the published Core SHA-256 in CI;
- registers the Tools module against API 2 when available;
- preserves a legacy API 1 registration path where the runtime can link it safely;
- keeps Core classes compile-only and outside the PlexonTools JAR;
- deliberately reports `CORE_LEGACY` while local mining remains authoritative;
- preserves standalone loading and all 4.2 gameplay/persistence semantics.

## Stable authority requirements

Before changing the mining mode to `CORE_RUNTIME`, all of the following must be implemented and demonstrated:

- Core facts available in a documented phase early enough for mutable EXP behavior, or an equivalent safe mutation contract;
- final successful/cancelled outcome correlation;
- idempotent import or parity-safe fallback for existing PlexonTools provenance;
- falling-block provenance parity;
- Core identity path that does not materially regress the 4.2 active-context fast path;
- Area Mine recursion/cancellation tests;
- exact progress/level boundary tests;
- 4.2 vs 4.3 LOCAL vs 4.3 CORE Spark measurements;
- 5/10-player and mixed soak runtime validation.

Until those gates pass, the correct release state is **CANDIDATE READY — runtime validation pending**, not stable released.
