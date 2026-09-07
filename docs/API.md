# PlexonTools 4.1 Public API

PlexonTools 4.1.0 exposes a stable read-only API and two post-commit progression events. The API and events belong to PlexonTools and remain available when PlexonCore is not installed.

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

It extends `PlayerEvent`, is non-cancellable, and is fired after an accepted gameplay mutation has been committed to the authoritative in-memory registry.

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

No event is emitted when the current 4.0 rules reject progress, including wrong owner/world/tool, ignored targets, cancelled gameplay, rejected player-placed blocks, or excluded secondary behavior.

## Level-up event

Exact class:

```text
com.plexon.tools.event.PlexonToolLevelUpEvent
```

It extends `PlayerEvent`, is non-cancellable, and is emitted after the committed level transition. Accessors include player, tool ID/category, old/new level, event ID, transaction ID, instance UUID, and bound world.

Production 4.0.0 intentionally permits at most one level advancement per accepted action and discards overflow at the boundary. 4.1.0 preserves that behavior; therefore one accepted transaction emits at most one level-up event.

## Event IDs

Every accepted progression operation receives one UUID transaction ID. Child event IDs are unique and derived from that transaction:

```text
<transaction>:progress
<transaction>:level:<old>-<new>
```

Listener failures occur after state commit and never roll back PlexonTools progress.
