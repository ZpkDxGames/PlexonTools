package com.plexon.tools.listener;

import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.service.ToolActivationService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ToolProtectionListener implements Listener {
    private final JavaPlugin plugin;
    private final ToolItemService itemService;
    private final ToolActivationService activations;

    public ToolProtectionListener(
            JavaPlugin plugin,
            ToolItemService itemService,
            ToolActivationService activations
    ) {
        this.plugin = plugin;
        this.itemService = itemService;
        this.activations = activations;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (itemService.isTagged(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(PlayerItemDamageEvent event) {
        if (itemService.isTagged(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Set<UUID> alreadyKept = new HashSet<>();
        event.getItemsToKeep().stream()
                .map(itemService::read)
                .flatMap(java.util.Optional::stream)
                .map(ToolState::instanceId)
                .forEach(alreadyKept::add);

        var iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (!itemService.isTagged(item)) {
                continue;
            }
            ToolState state = itemService.read(item).orElse(null);
            iterator.remove();
            if (!event.getKeepInventory()
                    && (state == null || alreadyKept.add(state.instanceId()))) {
                event.getItemsToKeep().add(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContainerClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        int topSize = event.getView().getTopInventory().getSize();
        boolean topSlot = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
        boolean outsideInventory = event.getRawSlot() < 0 || event.getClickedInventory() == null;
        InventoryType topType = event.getView().getTopInventory().getType();
        boolean externalInventory = topType != InventoryType.CRAFTING
                && topType != InventoryType.CREATIVE;

        if (itemService.isTagged(cursor) && (topSlot || outsideInventory)) {
            event.setCancelled(true);
            return;
        }
        if (itemService.isTagged(current)) {
            if (topSlot || (externalInventory && event.isShiftClick())
                    || event.getClick() == ClickType.DROP
                    || event.getClick() == ClickType.CONTROL_DROP
                    || event.getAction() == InventoryAction.CLONE_STACK) {
                event.setCancelled(true);
                return;
            }
        }
        if (topSlot && event.getHotbarButton() >= 0
                && itemService.isTagged(player.getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
            return;
        }
        if (topSlot && event.getClick() == ClickType.SWAP_OFFHAND
                && itemService.isTagged(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onContainerDrag(InventoryDragEvent event) {
        if (!itemService.isTagged(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getItem().getItemStack();
        if (!itemService.isTagged(item)) {
            return;
        }
        ToolState state = itemService.read(item).orElse(null);
        if (state == null || !state.ownerId().equals(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reconcileNextTick(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        reconcileNextTick(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        reconcileNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Adopt/refresh cursor state before Bukkit closes the inventory. If the
        // inventory is full, the active registry record safely restores it later.
        activations.reconcile(player);
        ItemStack cursor = player.getItemOnCursor();
        if (itemService.isTagged(cursor)) {
            int slot = player.getInventory().firstEmpty();
            if (slot >= 0) {
                player.getInventory().setItem(slot, cursor);
            }
            player.setItemOnCursor(null);
        }
    }

    private void reconcileNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                activations.reconcile(player);
            }
        });
    }
}
