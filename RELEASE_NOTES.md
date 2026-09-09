# PlexonTools 4.3.0 RC1 — PlexonCore 2 Migration Gate

PlexonTools 4.3.0 RC1 is a compatibility and release-gate candidate for the PlexonCore 2 runtime migration. It is intentionally **not** the stable `v4.3.0` release.

## Included in RC1

- Compiles against the exact released `PlexonCore-2.0.0.jar` rather than a mutable branch build.
- CI verifies the Core artifact SHA-256 before installing it as the compile-only dependency.
- Registers PlexonTools against the actual Core API 2 range when Core 2 is present.
- Retains a safe legacy Core 1 registration path where the older runtime can link the bridge.
- Keeps PlexonCore classes out of the PlexonTools JAR.
- Preserves the optimized PlexonTools 4.2 mining/progression path, public API/events, SQLite ownership, standalone fallback and existing player/tool data.
- Deliberately reports local/Core-legacy mining authority until the runtime parity gates pass.

## Why stable runtime authority is not enabled yet

Inspection of the released PlexonCore 2.0.0 source found three material release blockers:

1. **Event phase contract:** the Core block gateway acquires at `HIGHEST`, while PlexonTools' mutable block-EXP phase currently runs at `HIGH`. The Core context is therefore too late to replace that acquisition safely without changing the phase contract.
2. **Origin migration/parity:** Core owns a separate origin database and exposes no import API for existing PlexonTools placed-block provenance. It also does not yet reproduce PlexonTools falling-block provenance transitions.
3. **Identity performance:** Core namespace identity inspection currently reads ItemMeta/PDC on every routed break requesting identity, while PlexonTools 4.2 reuses `ActiveToolContext` and performs periodic compact revalidation. Runtime parity has not yet been measured.

The candidate does not hide these gaps behind a nominal `CORE_RUNTIME` state.

## Required validation before `v4.3.0`

- explicit mutable/final block-event correlation contract;
- historical origin import or parity-safe hybrid authority;
- falling-block anti-exploit parity;
- exact progression, cancellation and Area Mine recursion/protection tests;
- standalone and Core lifecycle tests;
- Spark A/B comparison: 4.2.0 vs 4.3 LOCAL vs 4.3 CORE;
- 5-player and 10-player concurrent mining checks;
- 30-minute mixed soak;
- understood identity and BlockDrop fallback rates.

See:

- `docs/CORE_RUNTIME_4_3_AUDIT.md`
- `docs/CORE_RUNTIME_4_3.md`
- `docs/ORIGIN_MIGRATION_4_3.md`
- `docs/PERFORMANCE_4_3_0.md`

## Compatibility

- Paper 26.2
- Java 25
- PlexonCore 2.0.0 for Core 2 validation
- PlexonCore remains compile-only/provided
- Existing PlexonTools SQLite data is retained
- Public `PlexonToolsAPI`, `PlexonToolProgressEvent`, and `PlexonToolLevelUpEvent` remain unchanged
- PlexonTools 4.2.0 remains the production rollback artifact

## Candidate installation

1. Back up the current PlexonTools JAR and `plugins/PlexonTools/` directory.
2. Install/verify PlexonCore 2.0.0 first.
3. Replace the PlexonTools JAR with the RC artifact.
4. Keep the existing PlexonTools configuration and database.
5. Start the server and inspect `/pt diagnostics`.
6. Treat the RC as validation software until the runtime gates above are completed.
