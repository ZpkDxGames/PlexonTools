# PlexonTools 4.3.0

Stable GitHub repository closure of the accepted PlexonTools 4.3.0 RC / Phase 3 runtime line.

## Performance and reliability

- Preserves the optimized ordinary single-block Legendary Tool path with cached active identity/definition/state/ability context.
- Keeps progression authoritative in memory and persistence asynchronous/coalesced in bounded SQLite transactions.
- Keeps visual refreshes and compatible progress notifications coalesced while preserving exact totals and immediate level-up boundaries.
- Keeps natural/player-placed provenance decisions in memory; chunk hydration and persistence remain asynchronous.
- Keeps `UNKNOWN` provenance fail-closed for natural-only progression.
- Keeps latest-state and pending drop-context acceleration structures explicitly bounded.
- Keeps one shared passive-holder refresh task only when passive abilities require it.

## Area Mine safety boundary

Secondary `AREA_MINE_3X3` execution remains `DISABLED_SAFE`. PlexonTools does not synthesize recursive secondary `BlockBreakEvent` fan-out and does not directly mutate adjacent blocks until protection/provenance/downstream-event equivalence can be established. The gate is checked before neighbor-plane allocation.

## Compatibility

- Paper 26.2 / Java 25.
- Optional external PlexonCore 2.0.4; Legendary Tools gameplay and provenance remain local to PlexonTools.
- Existing v4.2.1 tool PDC identity, SQLite data, API/events, configuration compatibility and player-facing visual language are preserved.
- SQLite JDBC and required native libraries remain bundled in the plugin JAR; server/API dependencies remain external.

## Release verification

The stable publisher rebuilds the exact final `main` commit, requires a non-empty all-green test suite plus Javadocs/distribution checks, verifies Java class major 69, required APIs/events/SQLite natives, dependency isolation, SHA-256 and provenance, then downloads the published GitHub assets and verifies their checksum and exact commit provenance.

Live PlexonCraft runtime certification is a separate operational follow-up and may remain `NOT_EXECUTED` in release provenance.

Rollback baseline: `v4.2.1` / `6e7a285fba8c16fc647ccc22c2c8342b2eb96711`.
