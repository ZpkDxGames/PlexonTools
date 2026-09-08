# PlexonTools 4.1.1 — Performance & Stability

PlexonTools 4.1.1 is a targeted performance release for ordinary single-block mining with progressive legendary tools. The 3x3 Area Mine ability is preserved but is not the primary optimization target.

Authoritative progression still commits immediately in memory. Expensive secondary work is reduced or coalesced so rapid normal mining performs less repeated identity/state/integration work per accepted block.

## Performance

- Coalesced compatible high-frequency `PlexonToolProgressEvent` notifications over a short two-tick server window while preserving the exact total `amount` received downstream.
- Kept `PlexonToolLevelUpEvent` immediate and flushes pending progress before level boundaries so event ordering remains sensible.
- Reused the authoritative latest `ToolState` in `ProgressionService` instead of rebuilding registry-backed state snapshots repeatedly during the mining event chain.
- Reused the already-validated block-break tool context for the matching `BlockDropItemEvent` when Auto Smelt or Magnet is enabled, avoiding a second ItemMeta/PDC/registry/definition/world validation pass for that break.
- Changed normal tool progression natural-block checking to a non-mutating provenance lookup so the tracker cleanup phase performs the single authoritative consume instead of consuming the same block twice.
- Preserved the existing coalesced lore/PDC/action-bar visual refresh instead of rendering item metadata on every block.

## Integration & compatibility

- PlexonCore 1.0.0 / Core API `>=1.0 <2.0` remains optional with safe standalone fallback.
- `PlexonToolsAPI`, `PlexonToolProgressEvent`, and `PlexonToolLevelUpEvent` remain source/binary compatible.
- A `PlexonToolProgressEvent` may now represent multiple compatible accepted progression units through its existing `amount` field. Consumers must use the amount rather than assuming one event equals one block.
- PlexonQuests integration remains decoupled through the public event contract; no hard PlexonTools → PlexonQuests dependency was added.
- SQLite schema, WAL behavior, asynchronous runtime persistence, backup behavior, and shutdown drain remain unchanged.
- Paper 26.2 / Java 25.

## Validation status

Automated Gradle tests, compilation, fat-JAR integrity verification, required public class checks, Java bytecode target checks, and SHA-256 artifact generation are enforced by GitHub Actions.

The stable tag/release must not be published until the manual Paper 26.2 runtime/profile gates are completed: ordinary mining, high-speed mining, PlexonQuests exact progress totals, level-up boundaries, natural-block anti-exploit, world binding, restart persistence, PlexonCore diagnostics, and multi-player stress comparison against 4.1.0.

No percentage performance improvement is claimed without those runtime measurements.

## Upgrade

After the stable release gate passes, stop the server, back up `PlexonTools-4.1.0.jar` and `plugins/PlexonTools/`, replace only the plugin JAR with `PlexonTools-4.1.1.jar`, then start the server. Verify `/plexon modules`, `/plexon diagnostics`, `/quests diagnostics`, and `/pt diagnostics` before reopening normal play.
