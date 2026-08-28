package com.plexon.tools.service;

import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.storage.InstanceRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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

    public boolean grant(Player target, ToolDefinition definition, boolean notifyTarget) {
        String world = target.getWorld().getName();
        if (!definition.isAllowedWorld(world)) {
            return false;
        }

        ToolItemService.CreatedTool created = itemService.create(target, definition, world);
        registry.register(created.state(), target.getName());
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(created.item());
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
            messages.send(target, "inventory-full");
        }
        if (notifyTarget) {
            messages.send(target, "tool-received", Map.of(
                    "tool", definition.displayName(),
                    "world", messages.plain(world)
            ));
        }
        return true;
    }
}
