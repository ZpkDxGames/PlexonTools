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

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
        return resolve(item).stateOptional();
    }

    /**
     * Resolves state and tag validity from one compact ItemMeta inspection.
     * Registry state is authoritative whenever an instance UUID is known; a
     * stale item world is repaired by the next visual refresh instead of being
     * allowed to override the database-backed record.
     */
    public ToolResolution resolve(ItemStack item) {
        ToolItemService.ToolIdentityInspection inspection =
                itemService.inspectIdentity(item);
        if (!inspection.tagged()) {
            return ToolResolution.untagged();
        }
        ToolItemService.ToolIdentity identity = inspection.identity();
        if (identity == null) {
            return ToolResolution.invalid();
        }
        InstanceRegistry.InstanceRecord record = instanceRegistry.findCached(
                identity.instanceId());
        if (record != null) {
            if (record.toolId().equalsIgnoreCase(identity.toolId())
                    && record.ownerId().equals(identity.ownerId())) {
                return ToolResolution.resolved(record.state());
            }
            return ToolResolution.invalid();
        }
        return itemService.read(item)
                .map(ToolResolution::resolved)
                .orElseGet(ToolResolution::invalid);
    }

    public ToolState latestState(ToolState suppliedState) {
        InstanceRegistry.InstanceRecord record = instanceRegistry.findCached(
                suppliedState.instanceId());
        if (record == null
                || !record.toolId().equalsIgnoreCase(suppliedState.toolId())
                || !record.ownerId().equals(suppliedState.ownerId())) {
            return suppliedState;
        }
        return record.state();
    }

    public boolean canUse(Player player, ToolDefinition definition, ToolState state, boolean notify) {
        if (!definition.levels().containsKey(state.level())) {
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
        ToolState resolved = resolveState(item).orElseThrow(() ->
                new IllegalArgumentException("Item is not a valid Plexon tool."));
        return addProgressInternal(player, EquipmentSlot.HAND, definition,
                resolved, target, amount);
    }

    public ToolState addProgress(
            Player player,
            ItemStack item,
            EquipmentSlot hand,
            ToolDefinition definition,
            String target,
            long amount
    ) {
        ToolState resolved = resolveState(item).orElseThrow(() ->
                new IllegalArgumentException("Item is not a valid Plexon tool."));
        return addProgressInternal(player, hand, definition, resolved, target, amount);
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
        return addProgressInternal(player, hand, definition,
                latestState(suppliedState), target, amount);
    }

    /**
     * Applies progress from state resolved in the current server-thread event
     * phase, avoiding a redundant registry lookup.
     */
    public ToolState addResolvedProgress(
            Player player,
            EquipmentSlot hand,
            ToolDefinition definition,
            ToolState resolvedState,
            String target,
            long amount
    ) {
        return addProgressInternal(player, hand, definition,
                resolvedState, target, amount);
    }

    private ToolState addProgressInternal(
            Player player,
            EquipmentSlot hand,
            ToolDefinition definition,
            ToolState registryState,
            String target,
            long amount
    ) {
        ToolState current = registryState;
        if (!current.categoryId().equalsIgnoreCase(definition.category())) {
            current = current.withCategory(definition.category());
        }
        if (definition.levels().higherKey(current.level()) == null) {
            if (!current.equals(registryState)
                    || instanceRegistry.findCached(current.instanceId()) == null) {
                instanceRegistry.update(current, 0L, player.getName());
                queueVisual(player, hand, definition, current.instanceId());
            }
            return current;
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
            LocatedItem located = locate(player, updated.instanceId(), hand);
            if (located == null) {
                queueVisual(player, hand, definition, updated.instanceId());
            } else {
                ItemStack updatedItem = itemService.apply(
                        located.item(), definition, updated, player.getName());
                located.replace(player, updatedItem);
            }
            announceUpgrade(player, definition, updated);
        } else {
            queueVisual(player, hand, definition, updated.instanceId());
        }
        return updated;
    }

    private void queueVisual(
            Player player,
            EquipmentSlot hand,
            ToolDefinition definition,
            UUID instanceId
    ) {
        if (!pendingVisuals.containsKey(instanceId)) {
            pendingVisuals.put(instanceId, new PendingVisual(
                    player.getUniqueId(), instanceId, hand, definition));
        }
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
        Iterator<PendingVisual> iterator = pendingVisuals.values().iterator();
        while (iterator.hasNext()) {
            PendingVisual visual = iterator.next();
            iterator.remove();
            try {
                flushVisual(visual, sendActionBars);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not refresh a coalesced PlexonTools progress display", exception);
            }
        }
    }

    private void flushPlayer(Player player) {
        Iterator<PendingVisual> iterator = pendingVisuals.values().iterator();
        while (iterator.hasNext()) {
            PendingVisual visual = iterator.next();
            if (!visual.playerId().equals(player.getUniqueId())) {
                continue;
            }
            iterator.remove();
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
        InstanceRegistry.InstanceRecord record = instanceRegistry.findCached(
                visual.instanceId());
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
        if (sendActionBar && settings.progressActionBar()) {
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
        ToolItemService.ToolIdentity identity = itemService.inspectIdentity(item).identity();
        return identity != null && identity.instanceId().equals(instanceId);
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
            ToolDefinition definition
    ) {
    }

    public record ToolResolution(boolean tagged, ToolState state) {
        private static final ToolResolution UNTAGGED = new ToolResolution(false, null);
        private static final ToolResolution INVALID = new ToolResolution(true, null);

        public static ToolResolution untagged() {
            return UNTAGGED;
        }

        public static ToolResolution invalid() {
            return INVALID;
        }

        public static ToolResolution resolved(ToolState state) {
            return new ToolResolution(true, java.util.Objects.requireNonNull(state));
        }

        public Optional<ToolState> stateOptional() {
            return Optional.ofNullable(state);
        }
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
