# PlexonTools 4.3.0 — PlexonCore 2 Runtime Migration

## Release state

`4.3.0` is currently a **release candidate**. The source is prepared for the released PlexonCore 2.0.0 API, but Core Runtime mining authority is deliberately not enabled yet.

The production-safe mining path remains the optimized PlexonTools 4.2 local path while the Core 2 contract gaps recorded in `CORE_RUNTIME_4_3_AUDIT.md` are resolved and measured.

## Operating states in the candidate

- `STANDALONE`: PlexonCore absent, disabled, incompatible or not linkable. PlexonTools uses its local 4.2 services.
- `CORE_LEGACY`: PlexonCore module registration is active, including API 2 registration when Core 2 is installed, but PlexonTools mining acquisition remains local.
- `CORE_RUNTIME`: reserved for the later authority switch after all parity gates pass. The candidate must not claim this state prematurely.

## Core compatibility

The common bridge contains no PlexonCore runtime types. The Core-backed implementation is loaded reflectively only when PlexonCore is enabled.

Supported registration generations:

- legacy API range: `>=1.0 <2.0`;
- runtime API range: `>=2.0 <3.0`.

The build is compiled against the released `PlexonCore-2.0.0.jar`, but Core remains `compileOnly` and must never be shaded into PlexonTools.

## Threading

PlexonTools keeps all Bukkit mutation on the primary thread:

- inventory/player access;
- event cancellation;
- EXP mutation;
- progression and level boundary mutation;
- public Bukkit events;
- Area Mine block events;
- particles/sounds/action bar/item refresh.

SQLite persistence remains coalesced/asynchronous as in 4.2.

## Why authority remains local in RC1

The released Core block gateway dispatches immutable contexts at `HIGHEST`. The existing PlexonTools EXP phase is `HIGH`, and progression is finalized at `MONITOR` after the event's final cancellation state is known.

Core 2.0.0 does not expose both an earlier mutable acquisition contract and a final outcome callback. The candidate therefore does not move progression or EXP mutation to Core just to satisfy an architectural label.

## Reload

`/pt reload` continues to invalidate active contexts and reload PlexonTools definitions/settings transactionally. Because Core Runtime mining subscription is not authoritative in RC1, no Core subscription is rebuilt yet.

A later runtime-authoritative candidate must make subscription/extractor replacement atomic with definition reload and preserve the old live runtime on invalid configuration.

## Rollback

PlexonTools 4.2.0 remains the production rollback artifact. RC1 does not alter the tool-state schema or destructively remove PlexonTools provenance, so reverting to 4.2.0 keeps existing tool state and placed-block data intact.

## Promotion gate

Promote to stable `v4.3.0` only after:

1. the Core runtime contract supports the required event phases/correlation;
2. provenance import/fallback and falling-block parity are proven;
3. standalone/Core lifecycle tests pass;
4. exact progression/ability behavior passes;
5. equivalent Spark A/B measurements show no material regression;
6. 5-player, 10-player and mixed soak scenarios pass.
