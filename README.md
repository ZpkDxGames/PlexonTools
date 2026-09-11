# PlexonTools

**PlexonTools 4.3.0** is the stable Paper-native progression engine for unique, world-activated custom tools. Each tool keeps its own UUID, permanent owner, world/progression scope, activation state, level, aggregate progress, optional per-target counters, and SQLite-backed provenance.

Repository/source/release closure is independent from live PlexonCraft rollout. Runtime certification may remain `NOT_EXECUTED` in release provenance; that is deployment evidence, not a blocker for the verified GitHub stable artifact.

## Runtime

- Paper `26.2.build.121-stable`
- Java 25 / class major 69
- PlexonCore 2.0.4 optional ecosystem integration; Legendary Tools gameplay and provenance remain local to PlexonTools
- Bundled SQLite JDBC runtime; no external database service
- No NMS/CraftBukkit implementation access

## Stable 4.3.0 architecture

The accepted Phase 2/Phase 3 runtime line is preserved for stable 4.3.0:

- ordinary single-block mining resolves an active tool once, caches compact identity/definition/state/ability context, and avoids per-break scheduler work;
- gameplay events do not synchronously traverse YAML or SQLite;
- progression remains authoritative in memory while repeated persistence writes are coalesced into bounded asynchronous SQLite transactions;
- visual/PDC refreshes and compatible public progress events are coalesced without changing exact progression totals;
- natural/player-placed provenance is maintained in memory on the gameplay thread and asynchronously hydrated/persisted by chunk;
- `UNKNOWN` provenance fails closed for natural-only progression;
- the latest-state acceleration cache has an explicit hard bound while the instance registry remains authoritative;
- block-drop ability contexts are correlated by player + exact block position and hard-bounded;
- passive-holder refresh uses one shared repeating task only when passive abilities are configured;
- public `PlexonToolProgressEvent` and `PlexonToolLevelUpEvent` contracts remain intact.

## Area Mine safety gate

`AREA_MINE_3X3` definitions remain configuration-compatible, but secondary Area Mine execution is intentionally **`DISABLED_SAFE`** in stable 4.3.0.

PlexonTools no longer synthesizes recursive secondary `BlockBreakEvent` fan-out. It also does not directly mutate adjacent blocks until a future implementation can prove equivalent protection-plugin, provenance, Core observer, and downstream event behavior. The gate is checked before neighbor-plane allocation, so ordinary single-block mining does not pay secondary Area Mine work.

This is an explicit safety boundary, not an unfinished release step.

## Tracking and provenance

Supported tracking types include `BLOCKS_BROKEN`, `MOBS_KILLED`, `ITEMS_FARMED`, `FISH_CAUGHT`, `DAMAGE_DEALT`, and `BLOCKS_PLACED`.

Player-placed blocks are indexed and excluded from natural-only block/farming progression when natural-block filtering is enabled. The gameplay path performs in-memory provenance decisions only; persistence and chunk warming are asynchronous. `DISABLED` remains a distinct policy state when natural-block filtering is intentionally turned off.

## Persistence

`plexontools.db` stores activation entitlements, tool instances, normalized target progress, and natural/player-placed provenance. Runtime progression updates the loaded authoritative record in memory and marks it dirty. The async writer drains bounded batches, uses SQLite WAL when available, and drains/checkpoints the queue on shutdown.

Legacy `data.yml` migration remains one-way and backup-preserving. Existing v4.2.1 physical PDC identity and SQLite contracts remain compatible with 4.3.0.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/pt` | `plexontools.use` | Open the current-world activation menu |
| `/pt <category> [player]` | `plexontools.use`; target requires admin | Open one category |
| `/pt all [player]` | `plexontools.use`; target requires admin | Open the unified showcase |
| `/pt give <player> <tool_id> [world]` | `plexontools.give` | Grant a unique tool instance |
| `/pt gui` | `plexontools.gui` | Open the administrative dashboard |
| `/pt reload` | `plexontools.reload` | Preflight and publish a complete config generation |
| `/pt backup` | `plexontools.backup` | Flush and create a consistent SQLite backup |
| `/pt diagnostics` | `plexontools.diagnostics` | Show runtime/provenance/Core/persistence health |

Aliases remain `/plexontool` and `/plexontools`.

## Upgrade and rollback

Before production deployment, back up `plugins/PlexonTools/` including `plexontools.db`. Replace only the plugin JAR and retain existing configuration/data unless intentionally migrating settings.

Rollback baseline: `v4.2.1` / `6e7a285fba8c16fc647ccc22c2c8342b2eb96711`.

The historical Phase 2 certification record remains in [`docs/PHASE2_4_3_0_RC2.md`](docs/PHASE2_4_3_0_RC2.md). Live PlexonCraft validation remains an operational follow-up rather than a GitHub stable-release prerequisite.

## Build and release verification

With JDK 25 and Gradle 9.1.0, provision the pinned PlexonCore 2.0.4 API artifact and run:

```bash
gradle clean check javadoc build
```

Stable runtime output: `build/libs/PlexonTools-4.3.0.jar`.

GitHub CI additionally verifies accepted RC3 ancestry, a non-empty all-green test suite, Java class major 69, required API/events and bundled SQLite native libraries, non-shading of server/runtime APIs, checksum integrity, and provenance. The stable publisher accepts only the exact current `main` commit, rebuilds that source, publishes `v4.3.0`, downloads the published assets, and verifies their checksum/provenance before the release job can finish green.

## Documentation

- [Phase 2 / 4.3.0 architecture and certification record](docs/PHASE2_4_3_0_RC2.md)
- [Capabilities and configuration](docs/CAPABILITIES.md)
- [Administrative GUI](docs/ADMIN_EDITOR.md)
- [Architecture and persistence](docs/ARCHITECTURE.md)
- [Public API](docs/API.md)
- [PlexonCore integration](docs/PLEXONCORE.md)

## License

MIT — see [LICENSE](LICENSE).
