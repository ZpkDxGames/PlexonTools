# PlexonTools 4.3.0-rc.2 — Phase 2 certification record

Status: **RC / runtime certification pending**

Rollback baseline: `v4.2.1` / `6e7a285fba8c16fc647ccc22c2c8342b2eb96711`

Runtime target: Paper 26.2, Java 25, PlexonCore 2.0.4.

## Scope

This candidate is a narrow runtime-ownership, provenance, performance, correctness, diagnostics and release-certification update. It deliberately does not redesign the PlexonTools GUI, lore, Legendary Tool progression, materials, stages, commands or visual language.

SemVer remains the 4.3.0 minor line because the release adds operational/diagnostic behavior and changes the unsafe Area Mine execution contract. Since `v4.3.0-rc.1` already exists on a historical divergent branch, this candidate is `v4.3.0-rc.2`.

## Historical 4.3 work classification

The old `agent/4.3.0-core-runtime-migration` branch and `v4.3.0-rc.1` were audited but not merged. They diverged from the current production lineage and were based around the older Core migration direction.

- **KEEP:** the concept of explicit Core lifecycle/module integration where already present in current v4.2.1.
- **REWORK:** build/release verification against current PlexonCore 2.0.4 and current v4.2.1 source.
- **DROP for this RC:** moving Legendary Tools gameplay/origin authority to Core or consuming both Core and local mining paths simultaneously.
- **DROP for this RC:** architectural changes whose only benefit is centralization and that add per-block context/provenance work.

PlexonCore 2.0.4 has useful shared infrastructure, but its current block event gateway and provenance coverage do not yet justify replacing the mature PlexonTools local path. PlexonTools remains authoritative for Legendary Tools gameplay and provenance in this RC.

## Runtime ownership

Authority is intentionally explicit:

- Legendary Tool identity: **PlexonTools LOCAL**
- Legendary Tool requirements/progression/abilities: **PlexonTools LOCAL**
- Tool state and SQLite persistence: **PlexonTools LOCAL**
- Block provenance used for Legendary Tool progression: **PlexonTools LOCAL**
- PlexonCore 2.0.4: lifecycle/module integration and shared ecosystem status only for this candidate

No Core + local dual gameplay execution path is enabled. There is no SHADOW gameplay duplication.

## Ordinary single-block mining pipeline

The authoritative ordinary block path remains synchronous and intentionally small:

`BlockBreakEvent` → cached active tool context → compact PDC identity validation when due → cached definition/state → ownership/world validation → local provenance classification → progression calculation → in-memory authoritative mutation → dirty persistence aggregation → coalesced public progress/event work → coalesced visual refresh.

Important contracts:

- no scheduler handoff created for every ordinary block;
- no executor submission or future per ordinary block;
- no SQLite/file operation from the block listener;
- no YAML traversal from the ordinary block listener;
- no full lore/item rebuild for every normal progress increment;
- no duplicate Core/local gameplay decision path.

The existing mining profiler remains available for runtime isolation and aggregate stage timing. Unit/synthetic evidence is not treated as MSPT certification.

## Identity and physical item compatibility

The existing compact PDC identity envelope is preserved. Current physical Legendary Tools continue to carry the same tool ID, instance UUID, owner/binding data and full persisted state fields expected by v4.2.1. The release does not identify tools by lore, display name or material alone.

No mass item regeneration is required for the RC. Registry state remains authoritative once an instance is known. The additional latest-state acceleration cache is now hard-bounded to 16,384 entries; eviction only causes an O(1) registry-cache lookup and cannot discard authoritative progress.

## Provenance

PlexonTools preserves conservative origin states:

- `NATURAL`
- `PLAYER_PLACED`
- `UNKNOWN`
- `DISABLED`

`UNKNOWN` is never promoted to `NATURAL`. Natural-only progression rejects both `PLAYER_PLACED` and `UNKNOWN`. The historical `fail-closed-while-loading: false` setting remains readable for configuration compatibility but is ignored with a warning; Phase 2 always fails closed while provenance is unknown.

The local tracker retains placement, piston, explosion, falling-block and chunk lifecycle handling. Provenance DB loads remain asynchronous and batched; ordinary break classification is an in-memory lookup. Failures leave affected chunks UNKNOWN and retry without granting natural-only progression.

Diagnostics expose natural/placed/unknown/disabled/rejected decision totals, loaded/unknown chunks, pending loads, retries and failures without scanning the database.

## Area Mine / 3x3

The historical coordinator synthesized a secondary Bukkit `BlockBreakEvent` per neighboring block. Phase 2 forbids PlexonTools-owned recursive event fan-out and does not replace it with a direct block mutation that could bypass external protection semantics.

Therefore `4.3.0-rc.2` sets secondary Area Mine execution to **DISABLED_SAFE**. Existing requested mode values remain parseable for migration/diagnostics, but are not authoritative. The cached block ability profile excludes Area Mine while this gate is closed, and `mineArea` checks the gate before collecting neighboring blocks, so ordinary single-block mining pays essentially no secondary-area cost.

A future direct Area Mine implementation must prove per-block protection, provenance, drop/progression, event interoperability and bounded-volume equivalence before the gate can be reopened.

## Progression and visual coalescing

Authoritative progress is applied immediately to in-memory state. Coalescing only reduces secondary work; it never debounces away progress. A burst of valid increments still produces the exact accumulated authoritative value.

Normal progress refreshes are keyed by tool instance and coalesced into the existing visual task. Level/form threshold transitions retain immediate state transition semantics and public-event ordering. Expensive ItemMeta/lore rendering is therefore not tied one-for-one to ordinary block breaks.

## Persistence

The v4.2.1 persistence architecture is retained:

- in-memory authoritative state;
- dirty revision/coalescing by instance;
- bounded pending-write structures;
- bounded transaction batches;
- periodic/shared async flush;
- pressure-triggered shared flush rather than task-per-break;
- logout/shutdown deterministic flush boundaries;
- no live Bukkit `Player`, `Block`, `World` or `ItemStack` passed to the SQLite worker.

The release does not introduce a SQLite/file write, connection acquisition, fsync or executor submission per block.

## Protection interoperability

For ordinary mining, PlexonTools honors the root Bukkit `BlockBreakEvent` cancellation contract and does not uncancel blocked events. PlexonTools does not add a speculative direct WorldGuard/FAWE dependency in this RC.

For Area Mine, direct mutation is not enabled because no protection-equivalent direct integration is currently certified. Failing closed is preferred to bypassing or replaying recursive Bukkit events.

## Configuration compilation and reload

Normal gameplay consumes compiled repository/state objects rather than parsing YAML in the mining hot path.

`/pt reload` now performs a complete detached preflight before pausing/mutating the live services:

1. fingerprint all five configuration resources;
2. strictly load base settings;
3. load detached messages/categories/tools/world menus;
4. reject configured entries that were skipped during repository compilation;
5. fingerprint again and reject concurrent file changes;
6. return the exact validated fingerprint;
7. verify it immediately before application;
8. apply repositories/services once;
9. verify the fingerprint again;
10. restart the bounded runtime tasks only after successful application.

Repeated reload uses the same listener registrations and restarts rather than multiplying listeners/workers. Malformed candidates are rejected before live mutation.

## API and PlaceholderAPI

The existing public `PlexonToolsAPI`, `PlexonToolProgressEvent` and `PlexonToolLevelUpEvent` contracts are preserved. Progress events continue to observe already-committed state and level-up ordering is preserved.

PlexonTools v4.2.1 does not expose a direct PlaceholderAPI expansion in this repository, so Phase 2 does not invent one as part of the performance RC. PlaceholderAPI is also verified as non-shaded if present on a consumer server.

## Diagnostics

`/pt diagnostics` now exposes low-overhead operational state including:

- plugin/Paper/Java version;
- explicit LOCAL gameplay/provenance authority;
- Core state/version/API;
- definitions and tracked instances;
- persistence queue/commit/high-water/failure metrics;
- natural provenance cache/load/decision metrics;
- ability/drop-context state;
- requested/effective Area Mine state;
- last reload state/time;
- mining profiler status;
- public API/event availability.

The existing mining profiler provides aggregate/capped stage samples for identity, provenance, progression, visual/persistence and related mining phases. It does not log every break.

## Migration from v4.2.1

Upgrade compatibility is intentionally conservative:

- physical PDC identity is preserved;
- definition IDs are not rewritten for the RC;
- instance UUID/owner/world/level/progress/form/enchant/stats remain compatible;
- the existing SQLite database/schema remains in use;
- no destructive migration is introduced;
- keep `plugins/PlexonTools/` and its database/configuration when testing the RC;
- back up the directory/database before replacing the JAR.

Behavioral migration note: any legacy Area Mine requested mode becomes `DISABLED_SAFE` in this RC. Any configuration requesting fail-open UNKNOWN provenance is ignored and becomes fail-closed.

Rollback remains `v4.2.1` at `6e7a285fba8c16fc647ccc22c2c8342b2eb96711`.

## Automated/distribution certification

The CI/release contract requires:

- Java 25 / class major 69;
- exact candidate version `4.3.0-rc.2`;
- all JUnit tests green;
- static architecture regression contracts for known hot-path anti-patterns;
- required plugin/API/event/SQLite entries in the JAR;
- PlexonCore/Paper/Bukkit/Adventure/PlaceholderAPI/WorldGuard/FAWE not improperly shaded;
- SHA-256 verification;
- `TEST_SUMMARY.txt`;
- `PROVENANCE.txt` containing the exact candidate SHA and rollback boundary.

The GitHub release is a prerelease only. Its tag must target the exact source candidate. Stable `v4.3.0` must remain absent until runtime certification.

## PlexonCraft runtime certification still required

Stable promotion is blocked until real PlexonCraft testing covers at least:

- representative v4.2.1 upgrade and existing Legendary Tool preservation;
- startup with PlexonCore 2.0.4;
- ordinary and sustained single-block mining;
- NATURAL, PLAYER_PLACED and UNKNOWN provenance behavior;
- owner/binding/world restrictions;
- progression, level/form transitions and enchant behavior;
- persistence restart and logout/reconnect;
- visual coalescing with no lost progression;
- Area Mine safe-disabled behavior (and enabled behavior only if separately reimplemented/certified);
- protection/interoperability, including external-edit provenance where applicable;
- public API/event compatibility;
- repeated valid reload and failed-reload retention;
- fresh Spark comparison between equivalent ordinary mining with and without PlexonTools processing;
- TPS, median/p95/p99/max MSPT where available, listener/Core/protection contribution, task counts, persistence behavior and allocation symptoms;
- at least 30 minutes of soak with no unbounded queue/scheduler growth and zero HIGH/CRITICAL runtime defects.

Historical MSPT observations and JVM unit tests are not runtime certification evidence.
