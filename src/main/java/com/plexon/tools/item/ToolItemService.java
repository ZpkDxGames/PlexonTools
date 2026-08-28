package com.plexon.tools.item;

import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.model.ToolLevel;
import com.plexon.tools.util.ProgressBar;
import com.plexon.tools.util.ProgressionMath;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ToolItemService {
    private final MessageService messages;
    private final PluginSettings settings;
    private final NamespacedKey idKey;
    private final NamespacedKey uuidKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey statCountKey;
    private final NamespacedKey boundWorldKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey profileHashKey;

    public ToolItemService(JavaPlugin plugin, MessageService messages, PluginSettings settings) {
        this.messages = messages;
        this.settings = settings;
        idKey = new NamespacedKey(plugin, "id");
        uuidKey = new NamespacedKey(plugin, "uuid");
        levelKey = new NamespacedKey(plugin, "level");
        statCountKey = new NamespacedKey(plugin, "stat_count");
        boundWorldKey = new NamespacedKey(plugin, "bound_world");
        ownerKey = new NamespacedKey(plugin, "owner");
        profileHashKey = new NamespacedKey(plugin, "profile_hash");
    }

    public CreatedTool create(Player owner, ToolDefinition definition, String boundWorld) {
        UUID instanceId = UUID.randomUUID();
        ToolState state = new ToolState(definition.id(), instanceId,
                definition.firstLevel().number(), 0L, boundWorld, owner.getUniqueId());
        ItemStack item = apply(ItemStack.of(definition.baseMaterial()), definition, state);
        return new CreatedTool(item, state);
    }

    public Optional<ToolState> read(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(idKey, PersistentDataType.STRING);
        String rawUuid = pdc.get(uuidKey, PersistentDataType.STRING);
        Integer level = pdc.get(levelKey, PersistentDataType.INTEGER);
        Long progress = pdc.get(statCountKey, PersistentDataType.LONG);
        String boundWorld = pdc.get(boundWorldKey, PersistentDataType.STRING);
        String rawOwner = pdc.get(ownerKey, PersistentDataType.STRING);
        if (id == null || rawUuid == null || level == null || progress == null
                || boundWorld == null || rawOwner == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ToolState(id, UUID.fromString(rawUuid), Math.max(1, level),
                    Math.max(0L, progress), boundWorld, UUID.fromString(rawOwner)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean isTagged(ItemStack item) {
        return item != null
                && !item.getType().isAir()
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    public ItemStack apply(ItemStack item, ToolDefinition definition, ToolState state) {
        ToolLevel level = definition.level(state.level())
                .orElseThrow(() -> new IllegalArgumentException("Tool level is no longer configured: " + state.level()));
        Material material = level.material();
        ItemStack updatedItem = material.isItem() && item.getType() != material
                ? item.withType(material)
                : item;

        ItemMeta meta = updatedItem.getItemMeta();
        writeState(meta.getPersistentDataContainer(), state);
        meta.getPersistentDataContainer().set(profileHashKey, PersistentDataType.INTEGER,
                profileFingerprint(level));
        Map<String, String> placeholders = placeholders(definition, state);
        meta.displayName(messages.parse(level.displayName(), placeholders));
        List<Component> lore = level.lore().stream()
                .map(line -> messages.parse(line, placeholders))
                .toList();
        meta.lore(lore);

        for (Enchantment enchantment : new ArrayList<>(meta.getEnchants().keySet())) {
            meta.removeEnchant(enchantment);
        }
        level.enchantments().forEach((enchantment, enchantLevel) ->
                meta.addEnchant(enchantment, enchantLevel, true));
        meta.setUnbreakable(level.unbreakable());
        meta.setEnchantmentGlintOverride(level.glint().override());
        meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        if (level.hideEnchantments()) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (level.hideAttributes()) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        meta.setCustomModelData(level.customModelData());
        updatedItem.setItemMeta(meta);
        return updatedItem;
    }

    public ItemStack refreshProgress(ItemStack item, ToolDefinition definition, ToolState state) {
        ToolLevel level = definition.level(state.level())
                .orElseThrow(() -> new IllegalArgumentException("Tool level is no longer configured: " + state.level()));
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer storedProfileHash = pdc.get(profileHashKey, PersistentDataType.INTEGER);
        if (storedProfileHash == null || storedProfileHash != profileFingerprint(level)) {
            return apply(item, definition, state);
        }
        pdc.set(levelKey, PersistentDataType.INTEGER, state.level());
        pdc.set(statCountKey, PersistentDataType.LONG, state.progress());
        Map<String, String> placeholders = placeholders(definition, state);
        meta.displayName(messages.parse(level.displayName(), placeholders));
        meta.lore(level.lore().stream()
                .map(line -> messages.parse(line, placeholders))
                .toList());
        item.setItemMeta(meta);
        return item;
    }

    private void writeState(PersistentDataContainer pdc, ToolState state) {
        pdc.set(idKey, PersistentDataType.STRING, state.toolId());
        pdc.set(uuidKey, PersistentDataType.STRING, state.instanceId().toString());
        pdc.set(levelKey, PersistentDataType.INTEGER, state.level());
        pdc.set(statCountKey, PersistentDataType.LONG, state.progress());
        pdc.set(boundWorldKey, PersistentDataType.STRING, state.boundWorld());
        pdc.set(ownerKey, PersistentDataType.STRING, state.ownerId().toString());
    }

    private static int profileFingerprint(ToolLevel level) {
        String enchantments = level.enchantments().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getKey().toString()))
                .map(entry -> entry.getKey().getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
        return Objects.hash(level.displayName(), level.material(), enchantments, level.lore(),
                level.unbreakable(), level.glint(), level.hideEnchantments(),
                level.hideAttributes(), level.customModelData());
    }

    public Optional<ToolState> findBestOwned(Player player, String toolId) {
        return java.util.Arrays.stream(player.getInventory().getContents())
                .map(this::read)
                .flatMap(Optional::stream)
                .filter(state -> state.toolId().equalsIgnoreCase(toolId))
                .filter(state -> state.ownerId().equals(player.getUniqueId()))
                .max(Comparator.comparingInt(ToolState::level)
                        .thenComparingLong(ToolState::progress));
    }

    public Map<String, String> placeholders(ToolDefinition definition, ToolState state) {
        ToolLevel currentLevel = definition.level(state.level()).orElse(definition.firstLevel());
        boolean maximum = definition.nextLevel(state.level()).isEmpty();
        long required = maximum ? 0L : currentLevel.requirement();
        NavigableMap<Integer, Long> requirements = new java.util.TreeMap<>();
        definition.levels().forEach((number, level) -> requirements.put(number, level.requirement()));
        long completed = ProgressionMath.cumulativeBefore(state.level(), requirements);
        long total = ProgressionMath.saturatingAdd(completed, state.progress());
        long remaining = maximum ? 0L : ProgressionMath.remaining(state.progress(), required);
        int percent = maximum ? 100 : ProgressionMath.percent(state.progress(), required);
        String bar = ProgressBar.render(settings.progressBarWidth(), state.progress(), required,
                settings.progressFilledSymbol(), settings.progressEmptySymbol(),
                settings.progressFilledFormat(), settings.progressEmptyFormat());

        Map<String, String> values = new HashMap<>();
        values.put("tool_id", messages.plain(definition.id()));
        values.put("tool", definition.displayName());
        values.put("level_name", currentLevel.displayName());
        values.put("uuid", state.instanceId().toString());
        values.put("level", Integer.toString(state.level()));
        values.put("max_level", Integer.toString(definition.maxLevel()));
        values.put("current", Long.toString(state.progress()));
        values.put("required", maximum ? "MAX" : Long.toString(required));
        values.put("remaining", Long.toString(remaining));
        values.put("percent", Integer.toString(percent));
        values.put("total", Long.toString(total));
        values.put("next_level", maximum ? "MAX" : Integer.toString(state.level() + 1));
        values.put("world", messages.plain(state.boundWorld()));
        values.put("owner", state.ownerId().toString());
        values.put("tracking", messages.plain(definition.trackingType().displayName()));
        values.put("targets", messages.plain(definition.targetNames().isEmpty()
                ? "All" : String.join(", ", definition.targetNames())));
        values.put("material", currentLevel.material().name());
        values.put("enchantments", messages.plain(currentLevel.enchantments().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getKey().toString()))
                .map(entry -> entry.getKey().getKey().getKey().toUpperCase(java.util.Locale.ROOT)
                        + " " + entry.getValue())
                .collect(Collectors.joining(", "))));
        values.put("bar", bar);
        return values;
    }

    public record CreatedTool(ItemStack item, ToolState state) {
    }
}
