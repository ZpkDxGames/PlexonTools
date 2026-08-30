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
        return matchingInventoryState(player, definition.id(), worldName).isPresent()
                || registry.findOwned(player.getUniqueId(), definition.id(), worldName).stream()
                        .anyMatch(InstanceRegistry.InstanceRecord::active);
    }

    public boolean isAvailable(ToolDefinition definition, String worldName) {
        return definition != null && definition.enabled() && definition.isAllowedWorld(worldName)
                && (settings.worldMenuAutoShowAllowedTools()
                        || worldMenus.menuFor(worldName).contains(definition.id()));
    }

    public Optional<ToolState> stateFor(Player player, ToolDefinition definition, String worldName) {
        Optional<InventoryState> inventoryState = matchingInventoryState(
                player, definition.id(), worldName);
        if (inventoryState.isPresent()) {
            return Optional.of(inventoryState.get().state());
        }
        return registry.findOwned(player.getUniqueId(), definition.id(), worldName).stream()
                .findFirst()
                .map(InstanceRegistry.InstanceRecord::state);
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

        Optional<InventoryState> existing = matchingInventoryState(player, definition.id(), worldName);
        if (existing.isPresent()) {
            InventoryState inventoryState = existing.get();
            registry.register(inventoryState.state(), player.getName(), true);
            registry.update(inventoryState.state(), 0L, player.getName());
            registry.setActive(inventoryState.state().instanceId(), true);
            registry.setMenuManaged(inventoryState.state().instanceId(), true);
            ItemStack refreshed = itemService.refreshProgress(
                    inventoryState.item(), definition, inventoryState.state());
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

        List<InstanceRegistry.InstanceRecord> owned = registry.findOwned(
                player.getUniqueId(), definition.id(), worldName);
        ToolState state;
        ItemStack item;
        if (owned.isEmpty()) {
            ToolItemService.CreatedTool created = itemService.create(player, definition, worldName);
            state = created.state();
            item = created.item();
            registry.register(state, player.getName(), true, true);
        } else {
            InstanceRegistry.InstanceRecord record = owned.getFirst();
            state = record.state();
            item = itemService.restore(definition, state);
            registry.setActive(record.instanceId(), true);
            registry.setMenuManaged(record.instanceId(), true);
            owned.stream().skip(1).forEach(duplicate -> registry.setActive(duplicate.instanceId(), false));
        }
        inventory.setItem(slot, item);
        return ToggleResult.ACTIVATED;
    }

    public ToggleResult deactivate(Player player, ToolDefinition definition, String worldName) {
        removeMatchingItems(player, definition.id(), worldName, true);
        registry.findOwned(player.getUniqueId(), definition.id(), worldName)
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

        String currentWorld = player.getWorld().getName();
        Map<String, InstanceRegistry.InstanceRecord> preferred = new LinkedHashMap<>();
        for (InstanceRegistry.InstanceRecord record : registry.findActive(
                player.getUniqueId(), currentWorld)) {
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
            if (!state.boundWorld().equalsIgnoreCase(currentWorld)) {
                inventory.setItem(slot, null);
                continue;
            }

            InstanceRegistry.InstanceRecord selected = preferred.get(
                    state.toolId().toLowerCase(java.util.Locale.ROOT));
            ToolDefinition definition = tools.find(state.toolId()).orElse(null);
            if (selected == null || !selected.instanceId().equals(state.instanceId())
                    || definition == null || !definition.enabled()
                    || !definition.isAllowedWorld(currentWorld)
                    || (selected.menuManaged() && !isAvailable(definition, currentWorld))) {
                registry.setActive(state.instanceId(), false);
                inventory.setItem(slot, null);
                continue;
            }
            inventory.setItem(slot, itemService.refreshProgress(item, definition, state));
            present.put(state.instanceId(), slot);
        }

        ItemStack cursor = player.getItemOnCursor();
        cursorState = itemService.read(cursor).orElse(null);
        if (cursorState != null) {
            InstanceRegistry.InstanceRecord selected = preferred.get(
                    cursorState.toolId().toLowerCase(java.util.Locale.ROOT));
            ToolDefinition definition = tools.find(cursorState.toolId()).orElse(null);
            boolean valid = cursorState.ownerId().equals(player.getUniqueId())
                    && cursorState.boundWorld().equalsIgnoreCase(currentWorld)
                    && selected != null
                    && selected.instanceId().equals(cursorState.instanceId())
                    && !present.containsKey(cursorState.instanceId())
                    && definition != null && definition.enabled()
                    && definition.isAllowedWorld(currentWorld)
                    && (!selected.menuManaged() || isAvailable(definition, currentWorld));
            if (valid) {
                player.setItemOnCursor(itemService.refreshProgress(
                        cursor, definition, cursorState));
                present.put(cursorState.instanceId(), -1);
            } else {
                if (cursorState.boundWorld().equalsIgnoreCase(currentWorld)) {
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
            String toolId,
            String worldName
    ) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            ToolState state = itemService.read(item).orElse(null);
            if (state != null
                    && state.ownerId().equals(player.getUniqueId())
                    && state.toolId().equalsIgnoreCase(toolId)
                    && state.boundWorld().equalsIgnoreCase(worldName)) {
                return Optional.of(new InventoryState(slot, item, state));
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        ToolState cursorState = itemService.read(cursor).orElse(null);
        if (cursorState != null
                && cursorState.ownerId().equals(player.getUniqueId())
                && cursorState.toolId().equalsIgnoreCase(toolId)
                && cursorState.boundWorld().equalsIgnoreCase(worldName)) {
            return Optional.of(new InventoryState(-1, cursor, cursorState));
        }
        return Optional.empty();
    }

    private void removeMatchingItems(
            Player player,
            String toolId,
            String worldName,
            boolean persistLatestState
    ) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            ToolState state = itemService.read(item).orElse(null);
            if (state == null
                    || !state.ownerId().equals(player.getUniqueId())
                    || !state.toolId().equalsIgnoreCase(toolId)
                    || !state.boundWorld().equalsIgnoreCase(worldName)) {
                continue;
            }
            if (persistLatestState) {
                registry.register(state, player.getName(), false);
                registry.update(state, 0L, player.getName());
            }
            registry.setActive(state.instanceId(), false);
            inventory.setItem(slot, null);
        }
        ItemStack cursor = player.getItemOnCursor();
        ToolState cursorState = itemService.read(cursor).orElse(null);
        if (cursorState != null
                && cursorState.ownerId().equals(player.getUniqueId())
                && cursorState.toolId().equalsIgnoreCase(toolId)
                && cursorState.boundWorld().equalsIgnoreCase(worldName)) {
            if (persistLatestState) {
                registry.register(cursorState, player.getName(), false);
                registry.update(cursorState, 0L, player.getName());
            }
            registry.setActive(cursorState.instanceId(), false);
            player.setItemOnCursor(null);
        }
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
