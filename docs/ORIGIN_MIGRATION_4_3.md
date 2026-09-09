# PlexonTools 4.3.0 — Block Origin Migration

Status: **shadow/import design pending — PlexonTools local provenance remains authoritative**.

## Existing PlexonTools provenance

PlexonTools 4.2 stores sparse player-placed block provenance in its own persistence layer and keeps loaded-chunk state in memory. It handles:

- place and multi-place;
- successful break cleanup;
- piston movement;
- block/entity explosions;
- falling-block landing and item-drop transitions;
- chunk load/unload;
- fail-closed behavior while provenance is unresolved.

This data already exists on production worlds and must remain valid for rollback.

## Released Core 2 origin service

PlexonCore 2.0.0 provides a separate Core-owned `core_block_origin` index with `NATURAL`, `PLAYER_PLACED` and `UNKNOWN` states. Its high-frequency lookup is memory-only and the backing SQLite work is asynchronous.

The released service tracks place, break/removal, burn/fade, explosions, pistons and chunk lifecycle.

## Migration gap: historical data

Core 2.0.0 has no public import method for PlexonTools' existing placed-block provenance.

Therefore an upgraded chunk whose Core database contains no historical row can become Core-known and return `NATURAL`, even when the PlexonTools database has a historical player-placed row for that same position.

Treating that Core result as authoritative before migration would weaken anti-exploit behavior.

## Migration gap: falling blocks

Core 2.0.0 does not provide the falling-block provenance transitions that PlexonTools currently handles. A placed gravity block can move before being mined; provenance must follow the moved block rather than disappear.

## Required migration strategy

The stable implementation must use one of these safe approaches:

### Preferred — Core import API

Add an idempotent, bounded Core origin import primitive and import PlexonTools' known player-placed positions before Core authority is enabled for a chunk.

Requirements:

- chunk/batch bounded;
- restart-safe and idempotent;
- no synchronous database reads from block events;
- explicit import progress/diagnostics;
- Core chunk remains `UNKNOWN` or locally authoritative until import is committed;
- legacy PlexonTools data is retained through the 4.3 rollback window.

### Transitional — hybrid authority

Keep PlexonTools local origin authoritative for chunks/material transitions whose Core history is not proven, while collecting Core classifications in shadow mode. Core authority may be enabled only for a chunk once its migration state is explicit.

`UNKNOWN` must never be promoted to `NATURAL`.

## Shadow comparison categories

A later candidate should count, without duplicate progression mutation:

- `MATCH_NATURAL`;
- `MATCH_PLACED`;
- `MATCH_UNKNOWN`;
- `LOCAL_NATURAL_CORE_UNKNOWN`;
- `LOCAL_PLACED_CORE_UNKNOWN`;
- `DISAGREEMENT`.

Do not log every mismatch.

## Stable acceptance gate

Core origin authority is not accepted until:

- historical import/hybrid state is explicit;
- player-place exploit tests pass;
- piston tests pass;
- explosion tests pass;
- falling-block tests pass;
- restart persistence passes;
- UNKNOWN remains fail-closed;
- shadow mismatch rate is understood;
- no monotonic cache/context growth is observed.

RC1 intentionally leaves `NaturalBlockTracker` and its persisted data intact.
