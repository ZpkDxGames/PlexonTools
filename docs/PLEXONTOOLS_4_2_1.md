# PlexonTools 4.2.1

PlexonTools 4.2.1 is a Core 2.0.4 lifecycle/provenance maintenance release on the stable 4.2 line.

## Authority model

`LOCAL_AUTHORITY / CORE_REGISTERED`

PlexonTools continues to own mining events, progression, block-origin/provenance tracking, BlockBreak/BlockDrop correlation, abilities and SQLite persistence locally. PlexonCore is used for module lifecycle registration and diagnostics only.

This is intentional. The draft 4.3 Runtime migration remains blocked by unresolved provenance/event-phase/performance gates and is not promoted by this release.

## Fixed

- Supports PlexonCore API `>=1.0 <3.0`.
- Compiles and CI-verifies against exact PlexonCore 2.0.4.
- Uses owner-aware Core 2 module state updates and owner-scoped unregister cleanup.
- Retains Core 1 compatibility fallback.
- Keeps Core classes compile-only and unshaded.

## Unchanged

Mining hot paths, progression, abilities, natural/player-placed provenance, BlockDrop correlation, GUI/editor behavior, item/PDC identity, public API/events and SQLite data semantics are unchanged from 4.2.0.

## Certification boundary

- SOURCE READY
- CI/release verification: required before publication
- RUNTIME PERFORMANCE: NOT EXECUTED

No Spark/runtime performance claim is made without a real Paper server.
