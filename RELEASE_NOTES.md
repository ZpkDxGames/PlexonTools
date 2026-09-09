# PlexonTools 4.2.0 — Performance & Reliability

PlexonTools 4.2.0 builds on the 4.1.1 mining hot-path work and focuses on bounded concurrency, lifecycle safety, Area Mine containment, provenance efficiency, persistence observability, and reload reliability for Paper 26.2 / Java 25.

## Performance & reliability

- Reduced ordinary block-break overhead and avoided progression/provenance work for max-level tools while retaining their abilities.
- Replaced periodic all-online-player passive-effect scans with event-driven relevant-holder reconciliation and cached potion-effect resolution.
- Reworked Auto Smelt/Magnet drop correlation to use player + exact block position, bounded pending contexts, expiry, and lifecycle cleanup.
- Added `BulkBreakCoordinator` for Area Mine with exact-position deduplication, a maximum number of secondary blocks per activation, and a per-player/per-tick dispatch budget.
- Kept `STRICT_EVENTS` as the effective Area Mine mode so Bukkit cancellation and protection integrations remain authoritative. Requested `OPTIMIZED` mode fails safe to strict behavior until equivalent protection-safe hooks exist.
- Added natural-block provenance batch consume primitives, loaded/unknown/tracked-position snapshots, and bulk persistence enqueue support.
- Added persistence diagnostics for queue high-water, dirty mutations, committed batches/entries, average batch size, pressure flushes, failed writes, and snapshot/flush state.
- Restarted ability runtime caches/tasks after successful `/pt reload` so changed definitions take effect cleanly.
- Made `PluginSettings` reload candidate-based so malformed configuration cannot partially mutate active settings.

## Compatibility

- `PlexonToolsAPI`, `PlexonToolProgressEvent`, and `PlexonToolLevelUpEvent` remain compatible.
- Existing SQLite data remains valid; no database reset or schema replacement is required.
- Owner binding, world restrictions, natural-only progression, cancellation/protection behavior, and immediate authoritative progression remain intact.
- PlexonCore 1.0.0 integration and standalone fallback remain supported.
- Paper 26.2 / Java 25.

## Verification

The release workflow builds the exact `v4.2.0` tag using Java 25 and requires Gradle tests/checks, full release-JAR readability, public API/event classes, SQLite JDBC/native entries, absence of shaded PlexonCore runtime classes, Java 25 bytecode, and a verified SHA-256 checksum before creating the GitHub release.

The source audit and runtime validation matrix are documented in `docs/PERFORMANCE_4_2_0_AUDIT.md`. Live Spark/stress/soak profiling should continue on the production-equivalent server after deployment; no unmeasured percentage performance claim is made.

## Upgrade

1. Stop the Paper server.
2. Back up the current PlexonTools JAR and `plugins/PlexonTools/` directory.
3. Replace the old JAR with `PlexonTools-4.2.0.jar`.
4. Keep the existing configuration and `plexontools.db`; do not reset player data.
5. Start the server and verify `/pt diagnostics` before reopening normal play.
