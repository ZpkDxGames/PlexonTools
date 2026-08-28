package com.plexon.tools.listener;

import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.service.ProgressionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class ToolProgressListener implements Listener {
    private final ToolConfigRepository tools;
    private final ToolItemService itemService;
    private final ProgressionService progression;
    private final PluginSettings settings;

    public ToolProgressListener(
            ToolConfigRepository tools,
            ToolItemService itemService,
            ProgressionService progression,
            PluginSettings settings
    ) {
        this.tools = tools;
        this.itemService = itemService;
        this.progression = progression;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        Optional<ToolState> state = itemService.read(item);
        if (state.isEmpty()) {
            if (itemService.isTagged(item)) {
                event.setCancelled(settings.cancelBlockBreaks());
                progression.warnInvalid(player);
            }
            return;
        }

        Optional<ToolDefinition> definition = tools.find(state.get().toolId());
        if (definition.isEmpty() || !definition.get().enabled()) {
            event.setCancelled(settings.cancelBlockBreaks());
            progression.warnInvalid(player);
            return;
        }
        if (!progression.canUse(player, definition.get(), state.get(), true)) {
            event.setCancelled(settings.cancelBlockBreaks());
            return;
        }
        if (definition.get().tracks(event.getBlock().getType())) {
            progression.addProgress(player, item, definition.get(), 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        Optional<ToolState> state = itemService.read(item);
        if (state.isEmpty()) {
            return;
        }
        Optional<ToolDefinition> definition = tools.find(state.get().toolId());
        if (definition.isEmpty() || !definition.get().enabled()) {
            return;
        }
        if (progression.canUse(player, definition.get(), state.get(), false)
                && definition.get().tracks(event.getEntityType())) {
            progression.addProgress(player, item, definition.get(), 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        Optional<ToolState> state = itemService.read(item);
        if (state.isEmpty()) {
            if (itemService.isTagged(item)) {
                if (settings.cancelAttacks()) {
                    event.setCancelled(true);
                }
                progression.warnInvalid(player);
            }
            return;
        }
        Optional<ToolDefinition> definition = tools.find(state.get().toolId());
        if (definition.isEmpty() || !definition.get().enabled()) {
            if (settings.cancelAttacks()) {
                event.setCancelled(true);
            }
            progression.warnInvalid(player);
            return;
        }
        if (!progression.canUse(player, definition.get(), state.get(), true)
                && settings.cancelAttacks()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        Optional<ToolState> state = itemService.read(item);
        if (state.isEmpty()) {
            if (itemService.isTagged(item)) {
                if (settings.cancelInteractions()) {
                    event.setCancelled(true);
                }
                progression.warnInvalid(event.getPlayer());
            }
            return;
        }
        Optional<ToolDefinition> definition = tools.find(state.get().toolId());
        if (definition.isEmpty() || !definition.get().enabled()) {
            if (settings.cancelInteractions()) {
                event.setCancelled(true);
            }
            progression.warnInvalid(event.getPlayer());
            return;
        }
        if (!progression.canUse(event.getPlayer(), definition.get(), state.get(), true)
                && settings.cancelInteractions()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Optional<ToolState> state = itemService.read(event.getItem());
        if (state.isEmpty()) {
            if (itemService.isTagged(event.getItem())) {
                event.setCancelled(true);
                progression.warnInvalid(event.getPlayer());
            }
            return;
        }
        Optional<ToolDefinition> definition = tools.find(state.get().toolId());
        if (definition.isEmpty()
                || !definition.get().enabled()
                || !progression.canUse(event.getPlayer(), definition.get(), state.get(), true)) {
            event.setCancelled(true);
        }
    }
}
