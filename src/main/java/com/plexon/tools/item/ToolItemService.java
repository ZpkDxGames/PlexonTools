package com.plexon.tools.item;

import com.plexon.tools.config.CategoryRepository;
import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.model.LevelRequirement;
import com.plexon.tools.model.RequirementMode;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.model.ToolLevel;
import com.plexon.tools.util.ProgressBar;
import com.plexon.tools.util.ProgressionMath;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ToolItemService {
    private final MessageService messages;
    private final PluginSettings settings;
    private final CategoryRepository categories;
    private final NamespacedKey idKey;
    private final NamespacedKey uuidKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey statCountKey;
    private final NamespacedKey boundWorldKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey categoryKey;
    private final NamespacedKey statBreakdownKey;
    private final NamespacedKey profileHashKey;

    public ToolItemService(
            JavaPlugin plugin,
            MessageService messages,
            PluginSettings settings,
            CategoryRepository categories
    ) {
        this.messages = messages;
        this.settings = settings;
        this.categories = categories;
        idKey = new NamespacedKey(plugin, "id");
        uuidKey = new NamespacedKey(plugin, "uuid");
        levelKey = new NamespacedKey(plugin, "level");
        statCountKey = new NamespacedKey(plugin, "stat_count");
        boundWorldKey = new NamespacedKey(plugin, "bound_world");
        ownerKey = new NamespacedKey(plugin, "owner");
        categoryKey = new NamespacedKey(plugin, "category");
        statBreakdownKey = new NamespacedKey(plugin, "stat_breakdown");
        profileHashKey = new NamespacedKey(plugin, "profile_hash");
    }

    public CreatedTool create(Player owner, ToolDefinition definition, String boundWorld) {
        UUID instanceId = UUID.randomUUID();
        ToolState state = new ToolState(definition.id(), instanceId,
                definition.firstLevel().number(), 0L, boundWorld, owner.getUniqueId(),
                definition.category(), Map.of());
        ItemStack item = apply(ItemStack.of(definition.baseMaterial()), definition, state);
        return new CreatedTool(item, state);
    }

    public ItemStack restore(ToolDefinition definition, ToolState state) {
        return apply(ItemStack.of(definition.baseMaterial()), definition, state);
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
        String category = pdc.get(categoryKey, PersistentDataType.STRING);
        String rawBreakdown = pdc.get(statBreakdownKey, PersistentDataType.STRING);
        if (id == null || rawUuid == null || level == null || progress == null
                || boundWorld == null || rawOwner == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ToolState(id, UUID.fromString(rawUuid), Math.max(1, level),
                    Math.max(0L, progress), boundWorld, UUID.fromString(rawOwner),
                    category == null ? "" : category, decodeBreakdown(rawBreakdown)));
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
        writeState(meta.getPersistentDataContainer(), state, definition.category());
        meta.getPersistentDataContainer().set(profileHashKey, PersistentDataType.INTEGER,
                profileFingerprint(level));
        Map<String, String> placeholders = placeholders(definition, state);
        meta.displayName(messages.parse(level.displayName(), placeholders));
        meta.lore(renderLore(level.lore(), definition, state, placeholders));

        for (Enchantment enchantment : new ArrayList<>(meta.getEnchants().keySet())) {
            meta.removeEnchant(enchantment);
        }
        level.enchantments().forEach((enchantment, enchantLevel) ->
                meta.addEnchant(enchantment, enchantLevel, true));
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(level.glint().override());
        applyCleanTooltip(meta);
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
        writeState(pdc, state, definition.category());
        Map<String, String> placeholders = placeholders(definition, state);
        meta.displayName(messages.parse(level.displayName(), placeholders));
        meta.lore(renderLore(level.lore(), definition, state, placeholders));
        meta.setUnbreakable(true);
        applyCleanTooltip(meta);
        item.setItemMeta(meta);
        return item;
    }

    private void writeState(PersistentDataContainer pdc, ToolState state, String category) {
        pdc.set(idKey, PersistentDataType.STRING, state.toolId());
        pdc.set(uuidKey, PersistentDataType.STRING, state.instanceId().toString());
        pdc.set(levelKey, PersistentDataType.INTEGER, state.level());
        pdc.set(statCountKey, PersistentDataType.LONG, state.progress());
        pdc.set(boundWorldKey, PersistentDataType.STRING, state.boundWorld());
        pdc.set(ownerKey, PersistentDataType.STRING, state.ownerId().toString());
        pdc.set(categoryKey, PersistentDataType.STRING, category);
        if (state.targetProgress().isEmpty()) {
            pdc.remove(statBreakdownKey);
        } else {
            pdc.set(statBreakdownKey, PersistentDataType.STRING,
                    encodeBreakdown(state.targetProgress()));
        }
    }

    private static int profileFingerprint(ToolLevel level) {
        String enchantments = level.enchantments().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getKey().toString()))
                .map(entry -> entry.getKey().getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));
        return Objects.hash("3.5-clean-tooltip", level.displayName(), level.material(), enchantments, level.lore(),
                level.requirement(),
                level.unbreakable(), level.glint(), level.hideEnchantments(),
                level.hideAttributes(), level.customModelData(), level.abilities());
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
        LevelRequirement requirement = currentLevel.requirement();
        long required = requirement.requiredTotal();
        long credited = requirement.creditedProgress(state.progress(), state.targetProgress());
        long rawProgress = requirement.rawProgress(state.progress(), state.targetProgress());
        long completed = cumulativeBefore(definition, state.level());
        long total = ProgressionMath.saturatingAdd(completed, rawProgress);
        long remaining = maximum ? 0L : requirement.remaining(state.progress(), state.targetProgress());
        int percent = maximum ? 100 : requirement.percentage(state.progress(), state.targetProgress());
        String bar = ProgressBar.render(settings.progressBarWidth(), credited, maximum ? 0L : required,
                settings.progressFilledSymbol(), settings.progressEmptySymbol(),
                settings.progressFilledFormat(), settings.progressEmptyFormat());
        String requiredText = maximum ? "MAX" : Long.toString(required);
        String ownerName = Optional.ofNullable(Bukkit.getPlayer(state.ownerId()))
                .map(Player::getName)
                .orElseGet(() -> Optional.ofNullable(Bukkit.getOfflinePlayer(state.ownerId()).getName())
                        .orElse(state.ownerId().toString()));
        String goal = maximum ? "Maximum level reached" : goalDescription(definition, requirement);

        Map<String, String> values = new HashMap<>();
        values.put("tool_id", messages.plain(definition.id()));
        values.put("tool", definition.displayName());
        values.put("level_name", currentLevel.displayName());
        values.put("uuid", state.instanceId().toString());
        values.put("level", Integer.toString(state.level()));
        values.put("max_level", Integer.toString(definition.maxLevel()));
        values.put("current", Long.toString(credited));
        values.put("current_xp", Long.toString(credited));
        values.put("required", requiredText);
        values.put("required_xp", requiredText);
        values.put("remaining", Long.toString(remaining));
        values.put("percent", Integer.toString(percent));
        values.put("percentage", Integer.toString(percent));
        values.put("total", Long.toString(total));
        values.put("next_level", maximum ? "MAX" : Integer.toString(state.level() + 1));
        values.put("world", messages.plain(state.boundWorld()));
        values.put("bound_world", messages.plain(state.boundWorld()));
        values.put("owner", messages.plain(ownerName));
        values.put("owner_name", messages.plain(ownerName));
        values.put("owner_uuid", state.ownerId().toString());
        values.put("tracking", messages.plain(definition.trackingType().displayName()));
        values.put("category", messages.plain(definition.category()));
        values.put("category_name", categories.find(definition.category())
                .map(com.plexon.tools.model.ToolCategory::displayName)
                .orElse(messages.plain(humanizeTarget(definition.category()))));
        values.put("requirement_mode", requirement.mode().name());
        values.put("goal_type_description", messages.plain(goal));
        values.put("target_progress", messages.plain(targetProgressDescription(requirement, state)));
        values.put("targets", messages.plain(requirement.targets().isEmpty()
                ? "All" : String.join(", ", requirement.targets().keySet())));
        values.put("material", currentLevel.material().name());
        values.put("enchantments", messages.plain(currentLevel.enchantments().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getKey().toString()))
                .map(entry -> entry.getKey().getKey().getKey().toUpperCase(java.util.Locale.ROOT)
                        + " " + entry.getValue())
                .collect(Collectors.joining(", "))));
        values.put("bar", bar);
        values.put("progress_bar", bar);
        return values;
    }

    private List<Component> renderLore(
            List<String> template,
            ToolDefinition definition,
            ToolState state,
            Map<String, String> placeholders
    ) {
        List<Component> rendered = new ArrayList<>();
        for (String line : template) {
            if (line.contains("{requirement_lines}") || line.contains("<requirement_lines>")) {
                rendered.addAll(requirementLines(definition, state, placeholders));
            } else if ((line.contains("{goal_type_description}")
                    || line.contains("<goal_type_description>"))
                    && definition.level(state.level()).orElse(definition.firstLevel())
                            .requirement().mode() == RequirementMode.SPECIFIC) {
                LevelRequirement requirement = definition.level(state.level())
                        .orElse(definition.firstLevel()).requirement();
                if (requirement.targets().isEmpty()) {
                    rendered.add(messages.parse(line, placeholders));
                    continue;
                }
                requirement.targets().forEach((target, required) -> {
                    String humanized = humanizeTarget(target);
                    String goal = definition.trackingType()
                            == com.plexon.tools.model.TrackingType.DAMAGE_DEALT
                            ? "Deal " + required + " damage to " + humanized
                            : definition.trackingType().action() + " " + required + "x " + humanized;
                    Map<String, String> values = new HashMap<>(placeholders);
                    values.put("goal_type_description", messages.plain(goal));
                    rendered.add(messages.parse(line, values));
                });
            } else {
                rendered.add(messages.parse(line, placeholders));
            }
        }
        return List.copyOf(rendered);
    }

    private List<Component> requirementLines(
            ToolDefinition definition,
            ToolState state,
            Map<String, String> base
    ) {
        if (definition.nextLevel(state.level()).isEmpty()) {
            return List.of(messages.parse(settings.maximumRequirementLine(), base));
        }

        LevelRequirement requirement = definition.level(state.level())
                .orElse(definition.firstLevel()).requirement();
        List<Map<String, String>> rows = new ArrayList<>();
        if (requirement.mode() == RequirementMode.SPECIFIC && !requirement.targets().isEmpty()) {
            requirement.targets().forEach((target, required) -> {
                long current = Math.min(state.targetProgress().getOrDefault(target, 0L), required);
                String humanized = humanizeTarget(target);
                String goal = definition.trackingType()
                        == com.plexon.tools.model.TrackingType.DAMAGE_DEALT
                        ? "Deal " + required + " damage to " + humanized
                        : definition.trackingType().action() + " " + required + "x " + humanized;
                rows.add(requirementPlaceholders(base, definition, humanized, goal,
                        current, required));
            });
        } else {
            long required = requirement.requiredTotal();
            long current = requirement.creditedProgress(state.progress(), state.targetProgress());
            rows.add(requirementPlaceholders(base, definition,
                    requirement.targets().isEmpty()
                            ? "Any " + definition.trackingType().noun()
                            : requirement.targets().keySet().stream()
                                    .map(ToolItemService::humanizeTarget)
                                    .collect(Collectors.joining(", ")),
                    goalDescription(definition, requirement), current, required));
        }
        return rows.stream()
                .map(values -> messages.parse(settings.requirementLine(), values))
                .toList();
    }

    private Map<String, String> requirementPlaceholders(
            Map<String, String> base,
            ToolDefinition definition,
            String target,
            String goal,
            long current,
            long required
    ) {
        Map<String, String> values = new HashMap<>(base);
        values.put("requirement_action", messages.plain(definition.trackingType().action()));
        values.put("requirement_target", messages.plain(target));
        values.put("requirement_goal", messages.plain(goal));
        values.put("requirement_current", Long.toString(Math.max(0L, current)));
        values.put("requirement_required", Long.toString(Math.max(0L, required)));
        values.put("requirement_remaining", Long.toString(Math.max(0L, required - current)));
        values.put("requirement_percentage", Integer.toString(required <= 0L
                ? 100 : (int) Math.min(100.0D, current * 100.0D / required)));
        return values;
    }

    private static void applyCleanTooltip(ItemMeta meta) {
        meta.addItemFlags(
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_STORED_ENCHANTS,
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_PLACED_ON,
                ItemFlag.HIDE_DYE,
                ItemFlag.HIDE_ARMOR_TRIM
        );
    }

    private static long cumulativeBefore(ToolDefinition definition, int currentLevel) {
        long completed = 0L;
        for (ToolLevel level : definition.levels().headMap(currentLevel, false).values()) {
            completed = ProgressionMath.saturatingAdd(completed, level.requirement().requiredTotal());
        }
        return completed;
    }

    private static String goalDescription(ToolDefinition definition, LevelRequirement requirement) {
        String action = definition.trackingType().action();
        String noun = definition.trackingType().noun();
        if (requirement.mode() == RequirementMode.GENERAL && requirement.targets().isEmpty()) {
            return definition.trackingType() == com.plexon.tools.model.TrackingType.DAMAGE_DEALT
                    ? "Deal " + requirement.amount() + " total damage"
                    : action + " any " + requirement.amount() + " " + noun;
        }
        if (requirement.mode() == RequirementMode.GENERAL) {
            return action + " any " + requirement.amount() + " of "
                    + requirement.targets().keySet().stream()
                            .map(ToolItemService::humanizeTarget)
                            .collect(Collectors.joining(", "));
        }
        return action + " " + requirement.targets().entrySet().stream()
                .map(entry -> entry.getValue()
                        + (definition.trackingType() == com.plexon.tools.model.TrackingType.DAMAGE_DEALT
                                ? " damage to " : "x ")
                        + humanizeTarget(entry.getKey()))
                .collect(Collectors.joining(" + "));
    }

    private static String targetProgressDescription(LevelRequirement requirement, ToolState state) {
        if (requirement.mode() != RequirementMode.SPECIFIC) {
            return Long.toString(requirement.creditedProgress(state.progress(), state.targetProgress()));
        }
        if (requirement.targets().isEmpty()) {
            return "No targets configured";
        }
        return requirement.targets().entrySet().stream()
                .map(entry -> humanizeTarget(entry.getKey()) + " "
                        + Math.min(state.targetProgress().getOrDefault(entry.getKey(), 0L), entry.getValue())
                        + "/" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private static String humanizeTarget(String value) {
        return java.util.Arrays.stream(value.toLowerCase(Locale.ROOT).split("_"))
                .filter(word -> !word.isBlank())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static String encodeBreakdown(Map<String, Long> breakdown) {
        return breakdown.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> LevelRequirement.normalize(entry.getKey()) + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private static Map<String, Long> decodeBreakdown(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Map.of();
        }
        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (String entry : encoded.split(";")) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_]+")) {
                throw new IllegalArgumentException("Invalid target progress data.");
            }
            long value = Long.parseLong(parts[1]);
            if (value < 1L) {
                throw new IllegalArgumentException("Invalid target progress amount.");
            }
            String target = LevelRequirement.normalize(parts[0]);
            if (breakdown.putIfAbsent(target, value) != null) {
                throw new IllegalArgumentException("Duplicate target progress data.");
            }
        }
        return breakdown;
    }

    public record CreatedTool(ItemStack item, ToolState state) {
    }
}
