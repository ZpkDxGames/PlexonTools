# PlexonTools 4.3.0 — Performance Validation

Status: **RUNTIME MEASUREMENTS NOT EXECUTED — stable release blocked**.

The 4.3 migration exists to reduce duplicated acquisition work across Plexon modules. It must not assume that Core is faster merely because acquisition is centralized.

## Required A/B matrix

| Scenario | 4.2.0 baseline | 4.3.0 LOCAL | 4.3.0 CORE | Result |
|---|---:|---:|---:|---|
| idle | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | pending |
| ordinary 1 miner | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | pending |
| rapid 1 miner | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | pending |
| 5 miners | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | pending |
| 10 miners | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | pending |
| max-level tool | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | pending |
| 30-minute mixed soak | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | pending |

## Released Core 2 cost observation

Core 2.0.0's namespace identity resolver inspects the main-hand `ItemMeta`/PDC for every routed block break whose route requests identity data and enumerates keys for requested namespaces.

PlexonTools 4.2 already caches `ActiveToolContext` and normally performs its compact identity revalidation at a ten-tick safety interval rather than on every block.

This makes identity performance a release gate rather than an assumed optimization.

## Spark frames to compare

Record equivalent profiles for:

- total PlexonTools mining listener/runtime self time;
- Core gateway/context/dispatch time;
- ItemMeta/PDC frames;
- local/Core provenance frames;
- `ProgressionService`;
- `AbilityService`;
- BlockDrop fallback/correlation;
- Area Mine secondary event routing.

## Stable performance gates

Stable `v4.3.0` requires:

- no material ordinary-mining P95/P99 regression versus 4.2.0;
- Core authority no slower than 4.3 LOCAL beyond measurement noise unless the shared-module gain is measured and explicitly justified;
- no unexplained repeating >5 ms PlexonTools synchronous spike;
- no synchronous DB/file I/O on mining events;
- no task-per-block async fanout;
- bounded queues/contexts with recovery;
- understood identity/drop fallback rates;
- exact progression totals and level boundaries.

## Candidate build verification

CI verification is separate from runtime performance validation. The RC build must still pass:

- Java 25 compilation/tests;
- exact PlexonCore 2.0.0 dependency checksum;
- JAR integrity;
- plugin metadata 4.3.0;
- public API/event class presence;
- SQLite runtime/native presence;
- no shaded PlexonCore classes;
- Java class major 69;
- JAR SHA-256 generation/check.

Passing CI does **not** promote this document's runtime rows from `NOT EXECUTED`.
