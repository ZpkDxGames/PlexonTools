package com.plexon.tools.service;

import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.config.WorldMenuRepository;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.storage.InstanceRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ToolActivationService {
    private final ToolConfigRepository tools;
    private final WorldMenuRepository worldMenus;
    private final PluginSettings settings;
    private final ToolItemService itemService;
    private final InstanceRegistry registry;
    private final MessageService messages;

    public ToolActivationService(
            ToolConfigRepository tools,
            WorldMenuRepository worldMenus,
            PluginSettings settings,
            ToolItemService itemService,
            InstanceRegistry registry,
            MessageService messages
    ) {
        this.tools = tools;
        this.worldMenus = worldMenus;
        this.settings = settings;
        this.itemService = itemService;
        this.registry = registry;
        this.messages = messages;
    }

    public boolean isActive(Player player, ToolDefinition definition, String worldName) {
        return matchingInventoryState(player, definition, worldName).isPresent()
                || ownedRecords(player, definition, worldName).stream()
                        .anyMatch(InstanceRegistry.InstanceRecord::active);
    }

    public boolean isAvailable(ToolDefinition definition, String worldName) {
        return definition != null && definition.enabled() && definition.isAllowedWorld(worldName)
                && (settings.worldMenuAutoShowAllowedTools()
                        || worldMenus.menuFor(worldName).contains(definition.id()));
    }

    public Optional<ToolState> stateFor(Player player, ToolDefinition definition, String worldName) {
        Optional<InventoryState> inventoryState = matchingInventoryState(
                player, definition, worldName);
        Optional<InstanceRegistry.InstanceRecord> canonical = ProgressionRecordSelector.canonical(
                definition, ownedRecords(player, definition, worldName));
        return canonical.map(InstanceRegistry.InstanceRecord::state)
                .or(() -> inventoryState.map(InventoryState::state));
    }

    public ToggleResult toggle(Player player, ToolDefinition definition, String worldName) {
        if (!isAvailable(definition, worldName)) {
            return ToggleResult.UNAVAILABLE;
        }
        return isActive(player, definition, worldName)
                ? deactivate(player, definition, worldName)
                : activate(player, definition, worldName);
    }

    public ToggleResult activate(Player player, ToolDefinition definition, String worldName) {
        if (!isAvailable(definition, worldName)) {
            return ToggleResult.UNAVAILABLE;
        }

        List<InstanceRegistry.InstanceRecord> owned = ownedRecords(
                player, definition, worldName);
        InstanceRegistry.InstanceRecord canonical = ProgressionRecordSelector.canonical(
                definition, owned).orElse(null);
        Optional<InventoryState> existing = matchingInventoryState(
                player, definition, worldName);
        if (existing.isPresent()) {
            InventoryState inventoryState = existing.get();
            ToolState selectedState = canonical == null
                    ? inventoryState.state() : canonical.state();
            selectedState = normalizeSharedState(
                    definition, selectedState, player.getName());
            registry.register(selectedState, player.getName(), true);
            registry.update(selectedState, 0L, player.getName());
            registry.setActive(selectedState.instanceId(), true);
            registry.setMenuManaged(selectedState.instanceId(), true);
            deactivateDuplicates(owned, selectedState.instanceId());
            ItemStack refreshed = itemService.refreshProgress(
                    inventoryState.item(), definition, selectedState,
                    player.getName());
            if (inventoryState.slot() < 0) {
                player.setItemOnCursor(refreshed);
            } else {
                player.getInventory().setItem(inventoryState.slot(), refreshed);
            }
            return ToggleResult.ACTIVATED;
        }

        PlayerInventory inventory = player.getInventory();
        int slot = inventory.firstEmpty();
        if (slot < 0) {
            return ToggleResult.INVENTORY_FULL;
        }

        ToolState state;
        ItemStack item;
        if (owned.isEmpty()) {
            ToolItemService.CreatedTool created = itemService.create(
                    player, definition, definition.persistenceWorld(worldName));
            state = created.state();
            item = created.item();
            registry.register(state, player.getName(), true, true);
        } else {
            InstanceRegistry.InstanceRecord record = canonical == null
                    ? owned.getFirst() : canonical;
            state = normalizeSharedState(
                    definition, record.state(), player.getName());
            item = itemService.restore(definition, state);
            registry.setActive(record.instanceId(), true);
            registry.setMenuManaged(record.instanceId(), true);
            deactivateDuplicates(owned, record.instanceId());
        }
        inventory.setItem(slot, item);
        return ToggleResult.ACTIVATED;
    }

    public ToggleResult deactivate(Player player, ToolDefinition definition, String worldName) {
        removeMatchingItems(player, definition, worldName, true);
        ownedRecords(player, definition, worldName)
                .forEach(record -> registry.setActive(record.instanceId(), false));
        return ToggleResult.DEACTIVATED;
    }

    public void reconcile(Player player) {
        PlayerInventory inventory = player.getInventory();

        // First adopt valid pre-3.5 items that are not yet represented in the registry database.
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            ToolState state = itemService.read(item).orElse(null);
            if (state != null
                    && state.ownerId().equals(player.getUniqueId())
                    && registry.find(state.instanceId()).isEmpty()) {
                registry.register(state, player.getName(), true);
            }
        }
        ToolState cursorState = itemService.read(player.getItemOnCursor()).orElse(null);
        if (cursorState != null
                && cursorState.ownerId().equals(player.getUniqueId())
                && registry.find(cursorState.instanceId()).isEmpty()) {
            registry.register(cursorState, player.getName(), true);
        }

        consolidateSharedProgress(player);
        String currentWorld = player.getWorld().getName();
        Map<String, InstanceRegistry.InstanceRecord> preferred = new LinkedHashMap<>();
        for (InstanceRegistry.InstanceRecord record : registry.findActive(
                player.getUniqueId())) {
            ToolDefinition definition = tools.find(record.toolId()).orElse(null);
            if (definition == null || (!definition.sharesProgressAcrossWorlds()
                    && !record.boundWorld().equalsIgnoreCase(currentWorld))
                    || (definition.sharesProgressAcrossWorlds()
                    && !definition.isAllowedWorld(currentWorld))) {
                continue;
            }
            String toolKey = record.toolId().toLowerCase(java.util.Locale.ROOT);
            if (preferred.putIfAbsent(toolKey, record) != null) {
                registry.setActive(record.instanceId(), false);
            }
        }

        Map<UUID, Integer> present = new LinkedHashMap<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            ToolState state = itemService.read(item).orElse(null);
            if (state == null) {
                continue;
            }
            if (!state.ownerId().equals(player.getUniqueId())) {
                inventory.setItem(slot, null);
                continue;
            }
            ToolDefinition definition = tools.find(state.toolId()).orElse(null);
            if (definition == null
                    || !definition.isAllowedWorld(currentWorld)
                    || (!definition.sharesProgressAcrossWorlds()
                    && !state.boundWorld().equalsIgnoreCase(currentWorld))) {
                registry.setActive(state.instanceId(), false);
                inventory.setItem(slot, null);
                continue;
            }

            InstanceRegistry.InstanceRecord selected = preferred.get(
                    state.toolId().toLowerCase(java.util.Locale.ROOT));
            if (selected == null || !selected.instanceId().equals(state.instanceId())
                    || present.containsKey(selected == null ? state.instanceId()
                            : selected.instanceId())
                    || !definition.enabled()
                    || (selected.menuManaged() && !isAvailable(definition, currentWorld))) {
                registry.setActive(state.instanceId(), false);
                inventory.setItem(slot, null);
                continue;
            }
            inventory.setItem(slot, itemService.refreshProgress(
                    item, definition, selected.state()));
            present.put(selected.instanceId(), slot);
        }

        ItemStack cursor = player.getItemOnCursor();
        cursorState = itemService.read(cursor).orElse(null);
        if (cursorState != null) {
            InstanceRegistry.InstanceRecord selected = preferred.get(
                    cursorState.toolId().toLowerCase(java.util.Locale.ROOT));
            ToolDefinition definition = tools.find(cursorState.toolId()).orElse(null);
            boolean valid = cursorState.ownerId().equals(player.getUniqueId())
                    && definition != null
                    && definition.isAllowedWorld(currentWorld)
                    && (definition.sharesProgressAcrossWorlds()
                            || cursorState.boundWorld().equalsIgnoreCase(currentWorld))
                    && selected != null
                    && selected.instanceId().equals(cursorState.instanceId())
                    && !present.containsKey(cursorState.instanceId())
                    && definition.enabled()
                    && (!selected.menuManaged() || isAvailable(definition, currentWorld));
            if (valid) {
                player.setItemOnCursor(itemService.refreshProgress(
                        cursor, definition, selected.state()));
                present.put(selected.instanceId(), -1);
            } else {
                if (definition == null || definition.sharesProgressAcrossWorlds()
                        || cursorState.boundWorld().equalsIgnoreCase(currentWorld)) {
                    registry.setActive(cursorState.instanceId(), false);
                }
                player.setItemOnCursor(null);
            }
        }

        boolean inventoryFull = false;
        for (InstanceRegistry.InstanceRecord record : preferred.values()) {
            if (present.containsKey(record.instanceId())) {
                continue;
            }
            ToolDefinition definition = tools.find(record.toolId()).orElse(null);
            if (definition == null || !definition.enabled()
                    || !definition.isAllowedWorld(currentWorld)
                    || (record.menuManaged() && !isAvailable(definition, currentWorld))) {
                registry.setActive(record.instanceId(), false);
                continue;
            }
            int slot = inventory.firstEmpty();
            if (slot < 0) {
                inventoryFull = true;
                continue;
            }
            inventory.setItem(slot, itemService.restore(definition, record.state()));
        }
        if (inventoryFull) {
            messages.send(player, "activation-inventory-full");
        }
    }

    private Optional<InventoryState> matchingInventoryState(
            Player player,
            ToolDefinition definition,
            String worldName
    ) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            ToolState state = itemService.read(item).orElse(null);
            if (state != null
                    && state.ownerId().equals(player.getUniqueId())
                    && state.toolId().equalsIgnoreCase(definition.id())
                    && (definition.sharesProgressAcrossWorlds()
                            || state.boundWorld().equalsIgnoreCase(worldName))) {
                return Optional.of(new InventoryState(slot, item, state));
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        ToolState cursorState = itemService.read(cursor).orElse(null);
        if (cursorState != null
                && cursorState.ownerId().equals(player.getUniqueId())
                && cursorState.toolId().equalsIgnoreCase(definition.id())
                && (definition.sharesProgressAcrossWorlds()
                        || cursorState.boundWorld().equalsIgnoreCase(worldName))) {
            return Optional.of(new InventoryState(-1, cursor, cursorState));
        }
        return Optional.empty();
    }

    private void removeMatchingItems(
            Player player,
            ToolDefinition definition,
            String worldName,
            boolean persistLatestState
    ) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            ToolState state = itemService.read(item).orElse(null);
            if (state == null
                    || !state.ownerId().equals(player.getUniqueId())
                    || !state.toolId().equalsIgnoreCase(definition.id())
                    || (!definition.sharesProgressAcrossWorlds()
                            && !state.boundWorld().equalsIgnoreCase(worldName))) {
                continue;
            }
            if (persistLatestState && registry.find(state.instanceId()).isEmpty()) {
                registry.register(state, player.getName(), false);
            }
            registry.setActive(state.instanceId(), false);
            inventory.setItem(slot, null);
        }
        ItemStack cursor = player.getItemOnCursor();
        ToolState cursorState = itemService.read(cursor).orElse(null);
        if (cursorState != null
                && cursorState.ownerId().equals(player.getUniqueId())
                && cursorState.toolId().equalsIgnoreCase(definition.id())
                && (definition.sharesProgressAcrossWorlds()
                        || cursorState.boundWorld().equalsIgnoreCase(worldName))) {
            if (persistLatestState && registry.find(cursorState.instanceId()).isEmpty()) {
                registry.register(cursorState, player.getName(), false);
            }
            registry.setActive(cursorState.instanceId(), false);
            player.setItemOnCursor(null);
        }
    }

    private List<InstanceRegistry.InstanceRecord> ownedRecords(
            Player player,
            ToolDefinition definition,
            String worldName
    ) {
        return definition.sharesProgressAcrossWorlds()
                ? registry.findOwned(player.getUniqueId(), definition.id())
                : registry.findOwned(player.getUniqueId(), definition.id(), worldName);
    }

    private void consolidateSharedProgress(Player player) {
        Map<String, ToolDefinition> activeSharedDefinitions = new LinkedHashMap<>();
        for (InstanceRegistry.InstanceRecord active : registry.findActive(player.getUniqueId())) {
            tools.find(active.toolId())
                    .filter(ToolDefinition::sharesProgressAcrossWorlds)
                    .ifPresent(definition -> activeSharedDefinitions.putIfAbsent(
                            definition.id().toLowerCase(java.util.Locale.ROOT), definition));
        }
        for (ToolDefinition definition : activeSharedDefinitions.values()) {
            List<InstanceRegistry.InstanceRecord> owned = registry.findOwned(
                    player.getUniqueId(), definition.id());
            InstanceRegistry.InstanceRecord canonical = ProgressionRecordSelector.canonical(
                    definition, owned).orElse(null);
            if (canonical == null) {
                continue;
            }
            normalizeSharedState(definition, canonical.state(), player.getName());
            boolean menuManaged = owned.stream()
                    .filter(InstanceRegistry.InstanceRecord::active)
                    .anyMatch(InstanceRegistry.InstanceRecord::menuManaged);
            registry.setActive(canonical.instanceId(), true);
            if (menuManaged) {
                registry.setMenuManaged(canonical.instanceId(), true);
            }
            deactivateDuplicates(owned, canonical.instanceId());
        }
    }

    private void deactivateDuplicates(
            List<InstanceRegistry.InstanceRecord> records,
            UUID canonicalId
    ) {
        records.stream()
                .filter(record -> !record.instanceId().equals(canonicalId))
                .forEach(record -> registry.setActive(record.instanceId(), false));
    }

    private ToolState normalizeSharedState(
            ToolDefinition definition,
            ToolState state,
            String ownerName
    ) {
        if (!definition.sharesProgressAcrossWorlds()
                || state.boundWorld().equalsIgnoreCase(
                        definition.progressionAnchorWorld())) {
            return state;
        }
        ToolState normalized = state.withBoundWorld(
                definition.progressionAnchorWorld());
        registry.update(normalized, 0L, ownerName);
        return normalized;
    }

    private record InventoryState(int slot, ItemStack item, ToolState state) {
    }

    public enum ToggleResult {
        ACTIVATED,
        DEACTIVATED,
        INVENTORY_FULL,
        UNAVAILABLE
    }
}
