package com.plexon.tools.service;

import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.model.LevelRequirement;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.storage.InstanceRegistry;
import com.plexon.tools.util.RequirementProgression;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns authoritative in-memory progression and coalesces expensive visual item
 * refreshes without moving Bukkit inventory operations off the server thread.
 */
public final class ProgressionService implements Listener {
    private final JavaPlugin plugin;
    private final ToolItemService itemService;
    private final InstanceRegistry instanceRegistry;
    private final PluginSettings settings;
    private final MessageService messages;
    private final Map<UUID, Long> lastWarnings = new HashMap<>();
    private final Map<UUID, PendingVisual> pendingVisuals = new LinkedHashMap<>();
    private final IdentityHashMap<ToolDefinition, NavigableMap<Integer, LevelRequirement>>
            requirementCache = new IdentityHashMap<>();
    private BukkitTask visualTask;

    public ProgressionService(
            JavaPlugin plugin,
            ToolItemService itemService,
            InstanceRegistry instanceRegistry,
            PluginSettings settings,
            MessageService messages
    ) {
        this.plugin = plugin;
        this.itemService = itemService;
        this.instanceRegistry = instanceRegistry;
        this.settings = settings;
        this.messages = messages;
    }

    public void start() {
        stopTask();
        long interval = settings.progressVisualRefreshTicks();
        visualTask = Bukkit.getScheduler().runTaskTimer(
                plugin, () -> flushPendingVisuals(true), interval, interval);
    }

    public void pause() {
        stopTask();
        flushPendingVisuals(false);
    }

    public void shutdown() {
        stopTask();
        flushPendingVisuals(false);
        pendingVisuals.clear();
        lastWarnings.clear();
    }

    public void clearDefinitionCaches() {
        requirementCache.clear();
    }

    /**
     * Resolves a tagged item's latest state from the registry cache. The full
     * item state is parsed only for pre-database or otherwise unregistered items.
     */
    public Optional<ToolState> resolveState(ItemStack item) {
        ToolItemService.ToolIdentity identity = itemService.readIdentity(item).orElse(null);
        if (identity == null) {
            return Optional.empty();
        }
        InstanceRegistry.InstanceRecord record = instanceRegistry.find(
                identity.instanceId()).orElse(null);
        if (record != null
                && record.toolId().equalsIgnoreCase(identity.toolId())
                && record.ownerId().equals(identity.ownerId())
                && record.boundWorld().equalsIgnoreCase(identity.boundWorld())) {
            return Optional.of(record.state());
        }
        return itemService.read(item);
    }

    public boolean canUse(Player player, ToolDefinition definition, ToolState state, boolean notify) {
        if (definition.level(state.level()).isEmpty()) {
            if (notify) {
                warnInvalid(player);
            }
            return false;
        }
        if (!state.ownerId().equals(player.getUniqueId())) {
            if (notify) {
                String ownerName = instanceRegistry.find(state.instanceId())
                        .map(InstanceRegistry.InstanceRecord::ownerName)
                        .orElse(state.ownerId().toString());
                warn(player, "owner-locked", Map.of("owner", messages.plain(ownerName)));
            }
            return false;
        }

        boolean validWorld = definition.isAllowedWorld(player.getWorld().getName());
        if (settings.enforceBoundWorld() && !definition.sharesProgressAcrossWorlds()) {
            validWorld = validWorld
                    && state.boundWorld().equalsIgnoreCase(player.getWorld().getName());
        }
        if (!validWorld && !player.hasPermission("plexontools.bypass.world")) {
            if (notify) {
                warn(player, "world-locked", Map.of(
                        "world", messages.plain(player.getWorld().getName())));
            }
            return false;
        }
        return true;
    }

    public ToolState addProgress(
            Player player,
            ItemStack item,
            ToolDefinition definition,
            String target,
            long amount
    ) {
        return addProgress(player, item, EquipmentSlot.HAND, definition,
                resolveState(item).orElseThrow(() ->
                        new IllegalArgumentException("Item is not a valid Plexon tool.")),
                target, amount);
    }

    public ToolState addProgress(
            Player player,
            ItemStack item,
            EquipmentSlot hand,
            ToolDefinition definition,
            String target,
            long amount
    ) {
        return addProgress(player, item, hand, definition,
                resolveState(item).orElseThrow(() ->
                        new IllegalArgumentException("Item is not a valid Plexon tool.")),
                target, amount);
    }

    public ToolState addProgress(
            Player player,
            ItemStack item,
            EquipmentSlot hand,
            ToolDefinition definition,
            ToolState suppliedState,
            String target,
            long amount
    ) {
        ToolState current = instanceRegistry.find(suppliedState.instanceId())
                .map(InstanceRegistry.InstanceRecord::state)
                .orElse(suppliedState);
        if (!current.categoryId().equalsIgnoreCase(definition.category())) {
            current = current.withCategory(definition.category());
        }

        RequirementProgression.Result result = RequirementProgression.advance(
                current.level(), current.progress(), current.targetProgress(), target, amount,
                requirementsFor(definition));
        ToolState updated = current.withProgress(
                result.level(), result.progress(), result.targetProgress());
        if (updated.equals(current)) {
            return current;
        }

        instanceRegistry.update(updated, amount, player.getName());
        if (result.levelsGained() > 0) {
            pendingVisuals.remove(updated.instanceId());
            ItemStack updatedItem = itemService.apply(
                    item, definition, updated, player.getName());
            setHeldItem(player, hand, updatedItem);
            announceUpgrade(player, definition, updated);
        } else {
            pendingVisuals.put(updated.instanceId(), new PendingVisual(
                    player.getUniqueId(), updated.instanceId(), hand, definition,
                    settings.progressActionBar()));
        }
        return updated;
    }

    public void warnInvalid(Player player) {
        warn(player, "invalid-tool-item", Map.of());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        flushPlayer(event.getPlayer());
        lastWarnings.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        flushPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        flushPlayer(event.getEntity());
    }

    private NavigableMap<Integer, LevelRequirement> requirementsFor(
            ToolDefinition definition
    ) {
        return requirementCache.computeIfAbsent(definition, ignored -> {
            NavigableMap<Integer, LevelRequirement> requirements = new TreeMap<>();
            definition.levels().forEach((number, level) ->
                    requirements.put(number, level.requirement()));
            return java.util.Collections.unmodifiableNavigableMap(requirements);
        });
    }

    private void flushPendingVisuals(boolean sendActionBars) {
        if (pendingVisuals.isEmpty()) {
            return;
        }
        List<PendingVisual> pending = new ArrayList<>(pendingVisuals.values());
        pendingVisuals.clear();
        for (PendingVisual visual : pending) {
            try {
                flushVisual(visual, sendActionBars);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not refresh a coalesced PlexonTools progress display", exception);
            }
        }
    }

    private void flushPlayer(Player player) {
        List<PendingVisual> selected = pendingVisuals.values().stream()
                .filter(visual -> visual.playerId().equals(player.getUniqueId()))
                .toList();
        selected.forEach(visual -> pendingVisuals.remove(visual.instanceId()));
        for (PendingVisual visual : selected) {
            try {
                flushVisual(visual, false);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not flush a PlexonTools progress display for "
                                + player.getName(), exception);
            }
        }
    }

    private void flushVisual(PendingVisual visual, boolean sendActionBar) {
        Player player = Bukkit.getPlayer(visual.playerId());
        if (player == null) {
            return;
        }
        InstanceRegistry.InstanceRecord record = instanceRegistry.find(
                visual.instanceId()).orElse(null);
        if (record == null) {
            return;
        }
        LocatedItem located = locate(player, visual.instanceId(), visual.preferredHand());
        if (located == null) {
            return;
        }
        ToolState state = record.state();
        ItemStack refreshed = itemService.refreshProgress(
                located.item(), visual.definition(), state, player.getName());
        located.replace(player, refreshed);
        if (sendActionBar && visual.actionBar() && settings.progressActionBar()) {
            messages.actionBar(player, "progress-update",
                    itemService.progressPlaceholders(visual.definition(), state));
        }
    }

    private LocatedItem locate(Player player, UUID instanceId, EquipmentSlot preferredHand) {
        if (preferredHand == EquipmentSlot.OFF_HAND) {
            ItemStack item = player.getInventory().getItemInOffHand();
            if (matches(item, instanceId)) {
                return LocatedItem.offHand(item);
            }
        } else {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (matches(item, instanceId)) {
                return LocatedItem.inventory(
                        item, player.getInventory().getHeldItemSlot());
            }
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (matches(storage[slot], instanceId)) {
                return LocatedItem.inventory(storage[slot], slot);
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (matches(offHand, instanceId)) {
            return LocatedItem.offHand(offHand);
        }
        ItemStack cursor = player.getItemOnCursor();
        return matches(cursor, instanceId) ? LocatedItem.cursor(cursor) : null;
    }

    private boolean matches(ItemStack item, UUID instanceId) {
        return itemService.readIdentity(item)
                .map(identity -> identity.instanceId().equals(instanceId))
                .orElse(false);
    }

    private static void setHeldItem(Player player, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
    }

    private void stopTask() {
        if (visualTask != null) {
            visualTask.cancel();
            visualTask = null;
        }
    }

    private void announceUpgrade(Player player, ToolDefinition definition, ToolState state) {
        Map<String, String> placeholders = Map.of(
                "tool", definition.level(state.level())
                        .map(com.plexon.tools.model.ToolLevel::displayName)
                        .orElse(definition.displayName()),
                "level", Integer.toString(state.level())
        );
        messages.sendWithoutPrefix(player, "level-up", placeholders);
        messages.actionBar(player, "level-up", placeholders);

        try {
            String configured = settings.levelUpSound().toLowerCase(Locale.ROOT);
            String namespaced = configured.contains(":")
                    ? configured
                    : "minecraft:" + configured.replace('_', '.');
            NamespacedKey key = NamespacedKey.fromString(namespaced);
            Sound sound = key == null ? null
                    : RegistryAccess.registryAccess().getRegistry(
                            RegistryKey.SOUND_EVENT).get(key);
            if (sound != null) {
                player.playSound(player.getLocation(), sound,
                        SoundCategory.PLAYERS, 1.0F, 1.15F);
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid configured sounds should never interrupt progression.
        }
        if (settings.levelUpParticles()) {
            player.getWorld().spawnParticle(Particle.END_ROD,
                    player.getLocation().add(0.0D, 1.0D, 0.0D),
                    24, 0.45D, 0.7D, 0.45D, 0.02D);
        }
    }

    private void warn(Player player, String key, Map<String, String> placeholders) {
        long now = System.currentTimeMillis();
        long previous = lastWarnings.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < settings.warningCooldownMillis()) {
            return;
        }
        lastWarnings.put(player.getUniqueId(), now);
        messages.actionBar(player, key, placeholders);
    }

    private record PendingVisual(
            UUID playerId,
            UUID instanceId,
            EquipmentSlot preferredHand,
            ToolDefinition definition,
            boolean actionBar
    ) {
    }

    private record LocatedItem(ItemStack item, int inventorySlot, boolean offHand, boolean cursor) {
        private static LocatedItem inventory(ItemStack item, int slot) {
            return new LocatedItem(item, slot, false, false);
        }

        private static LocatedItem offHand(ItemStack item) {
            return new LocatedItem(item, -1, true, false);
        }

        private static LocatedItem cursor(ItemStack item) {
            return new LocatedItem(item, -1, false, true);
        }

        private void replace(Player player, ItemStack replacement) {
            if (cursor) {
                player.setItemOnCursor(replacement);
            } else if (offHand) {
                player.getInventory().setItemInOffHand(replacement);
            } else {
                player.getInventory().setItem(inventorySlot, replacement);
            }
        }
    }
}
