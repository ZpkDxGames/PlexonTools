package com.plexon.tools.service;

import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.storage.InstanceRegistry;
import com.plexon.tools.util.ProgressionMath;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

public final class ProgressionService {
    private final ToolItemService itemService;
    private final InstanceRegistry instanceRegistry;
    private final PluginSettings settings;
    private final MessageService messages;
    private final Map<UUID, Long> lastWarnings = new HashMap<>();

    public ProgressionService(
            ToolItemService itemService,
            InstanceRegistry instanceRegistry,
            PluginSettings settings,
            MessageService messages
    ) {
        this.itemService = itemService;
        this.instanceRegistry = instanceRegistry;
        this.settings = settings;
        this.messages = messages;
    }

    public boolean canUse(Player player, ToolDefinition definition, ToolState state, boolean notify) {
        if (settings.enforceOwner()
                && !state.ownerId().equals(player.getUniqueId())
                && !player.hasPermission("plexontools.bypass.owner")) {
            if (notify) {
                String ownerName = instanceRegistry.find(state.instanceId())
                        .map(InstanceRegistry.InstanceRecord::ownerName)
                        .orElse(state.ownerId().toString());
                warn(player, "owner-locked", Map.of("owner", messages.plain(ownerName)));
            }
            return false;
        }

        boolean validWorld = definition.isAllowedWorld(player.getWorld().getName());
        if (settings.enforceBoundWorld()) {
            validWorld = validWorld && state.boundWorld().equalsIgnoreCase(player.getWorld().getName());
        }
        if (!validWorld && !player.hasPermission("plexontools.bypass.world")) {
            if (notify) {
                warn(player, "world-locked", Map.of("world", messages.plain(state.boundWorld())));
            }
            return false;
        }
        return true;
    }

    public ToolState addProgress(Player player, ItemStack item, ToolDefinition definition, long amount) {
        ToolState current = itemService.read(item)
                .orElseThrow(() -> new IllegalArgumentException("Item is not a valid Plexon tool."));

        NavigableMap<Integer, Long> requirements = new TreeMap<>();
        definition.levels().forEach((number, level) -> requirements.put(number, level.requirement()));
        ProgressionMath.Result result = ProgressionMath.advance(
                current.level(), current.progress(), amount, requirements);
        ToolState updated = current.withProgress(result.level(), result.progress());
        itemService.apply(item, definition, updated);
        player.getInventory().setItemInMainHand(item);
        instanceRegistry.update(updated, amount, player.getName());

        if (result.levelsGained() > 0) {
            announceUpgrade(player, definition, updated);
        }
        return updated;
    }

    public void warnInvalid(Player player) {
        warn(player, "invalid-tool-item", Map.of());
    }

    private void announceUpgrade(Player player, ToolDefinition definition, ToolState state) {
        Map<String, String> placeholders = Map.of(
                "tool", definition.displayName(),
                "level", Integer.toString(state.level())
        );
        messages.sendWithoutPrefix(player, "level-up", placeholders);
        messages.actionBar(player, "level-up", placeholders);

        try {
            Sound sound = Sound.valueOf(settings.levelUpSound().toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, 1.0F, 1.15F);
        } catch (IllegalArgumentException ignored) {
            // Invalid configured sounds should never interrupt progression.
        }
        if (settings.levelUpParticles()) {
            player.getWorld().spawnParticle(Particle.END_ROD,
                    player.getLocation().add(0.0D, 1.0D, 0.0D), 24, 0.45D, 0.7D, 0.45D, 0.02D);
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
}
