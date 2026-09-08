# PlexonTools 4.1 Public API

PlexonTools 4.1.1 exposes a stable read-only API and two post-commit progression events. The API and events belong to PlexonTools and remain available when PlexonCore is not installed.

## Service lookup

```java
var registration = Bukkit.getServicesManager().getRegistration(PlexonToolsAPI.class);
if (registration == null) return;
PlexonToolsAPI tools = registration.getProvider();
```

The service type is `com.plexon.tools.api.PlexonToolsAPI`.

## Read API

```java
Optional<ToolView> tool(ItemStack stack);
Optional<ToolView> tool(UUID instanceId);
Optional<ToolDefinitionView> definition(String toolId);
Collection<ToolDefinitionView> definitions();
boolean isPlexonTool(ItemStack stack);
```

`ToolView` and `ToolDefinitionView` are immutable snapshots. They never expose `InstanceRegistry`, database records, repositories, or other mutable implementation services.

All API calls should be made on the Paper server thread. This is required in particular for `ItemStack` inspection and also provides a consistent contract across reloads.

## Progress event

Exact class:

```text
com.plexon.tools.event.PlexonToolProgressEvent
```

It extends `PlayerEvent`, is non-cancellable, and is fired only after accepted progression has already been committed to authoritative in-memory state.

Starting with 4.1.1, compatible high-frequency progress notifications may be coalesced over a short server-tick window. One event can therefore represent multiple accepted progression units through its existing `amount` field. Consumers must add the reported amount rather than assume one event equals one block/action. The semantic total is unchanged: ten accepted units still produce a downstream total amount of ten.

Batch grouping keeps player, tool instance, tool ID, category, progress type, material/target metadata, and level distinct. Mixed materials or progress on opposite sides of a level boundary are not combined incorrectly. A level-up flushes older pending progress for the instance first and the level-up notification remains immediate.

Data includes player, stable tool ID/category, stable progress type, applied amount, current level, relevant Bukkit material when applicable, event ID, transaction ID, and tool instance UUID.

Compatibility aliases include:

```text
getPlayer()/player()
toolId()/getToolId()
amount()/delta()/progressDelta()/getAmount()
level()/newLevel()/getLevel()
toolCategory()/getToolCategory()/category()
progressType()/getProgressType()/type()
material()/getMaterial()
eventId()/getEventId()
transactionId()/getTransactionId()
toolInstanceId()/getToolInstanceId()
```

Stable progress type values are:

```text
blocks_broken
mobs_killed
items_farmed
fish_caught
damage_dealt
blocks_placed
```

No event is emitted when progression rules reject the action, including wrong owner/world/tool, ignored targets, cancelled gameplay, rejected player-placed blocks, or excluded secondary behavior.

## Level-up event

Exact class:

```text
com.plexon.tools.event.PlexonToolLevelUpEvent
```

It extends `PlayerEvent`, is non-cancellable, and is emitted immediately after the committed level transition. Accessors include player, tool ID/category, old/new level, event ID, transaction ID, instance UUID, and bound world.

Production behavior intentionally permits at most one level advancement per accepted action and discards overflow at the boundary. 4.1.1 preserves that behavior; therefore one accepted action emits at most one level-up event.

## Event IDs

Each emitted progress notification receives a UUID transaction ID. A batched notification covers the compatible accepted units represented by its `amount`. At a level boundary, the immediate progress notification and level-up event share the same transaction ID.

Child event IDs remain derived from the transaction:

```text
<transaction>:progress
<transaction>:level:<old>-<new>
```

Listener failures occur after state commit and never roll back PlexonTools progress.
