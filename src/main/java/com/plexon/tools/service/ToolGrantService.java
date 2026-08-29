package com.plexon.tools.service;

import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.storage.InstanceRegistry;
import org.bukkit.entity.Player;

import java.util.Map;

public final class ToolGrantService {
    private final ToolItemService itemService;
    private final InstanceRegistry registry;
    private final MessageService messages;

    public ToolGrantService(ToolItemService itemService, InstanceRegistry registry, MessageService messages) {
        this.itemService = itemService;
        this.registry = registry;
        this.messages = messages;
    }

    public GrantResult grant(Player target, ToolDefinition definition, boolean notifyTarget) {
        return grant(target, definition, target.getWorld().getName(), notifyTarget);
    }

    public GrantResult grant(
            Player target,
            ToolDefinition definition,
            String boundWorld,
            boolean notifyTarget
    ) {
        if (boundWorld == null || boundWorld.isBlank() || !definition.isAllowedWorld(boundWorld)) {
            return GrantResult.INVALID_WORLD;
        }
        int slot = target.getInventory().firstEmpty();
        if (slot < 0) {
            messages.send(target, "activation-inventory-full");
            return GrantResult.INVENTORY_FULL;
        }

        ToolItemService.CreatedTool created = itemService.create(target, definition, boundWorld);
        registry.register(created.state(), target.getName(), true);
        target.getInventory().setItem(slot, created.item());
        if (notifyTarget) {
            messages.send(target, "tool-received", Map.of(
                    "tool", definition.displayName(),
                    "world", messages.plain(boundWorld)
            ));
        }
        return GrantResult.GRANTED;
    }

    public enum GrantResult {
        GRANTED,
        INVALID_WORLD,
        INVENTORY_FULL
    }
}
