package com.plexon.tools.config;

import com.plexon.tools.model.GlintMode;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.model.ToolLevel;
import com.plexon.tools.model.TrackingType;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.StreamSupport;

public final class ToolConfigRepository {
    private static final String ID_PATTERN = "[a-z0-9_-]+";

    private final JavaPlugin plugin;
    private final File file;
    private volatile Map<String, ToolDefinition> definitions = Map.of();
    private YamlConfiguration yaml = new YamlConfiguration();

    public ToolConfigRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tools.yml");
    }

    public synchronized void reload() throws IOException, InvalidConfigurationException {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(file);
        Map<String, ToolDefinition> parsed = parseDefinitions(candidate, false);
        yaml = candidate;
        definitions = Collections.unmodifiableMap(parsed);
    }

    public Collection<ToolDefinition> all() {
        return definitions.values();
    }

    public Optional<ToolDefinition> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(normalizeId(id)));
    }

    public int size() {
        return definitions.size();
    }

    public List<Enchantment> enchantmentOptions() {
        Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        return StreamSupport.stream(registry.spliterator(), false)
                .sorted(java.util.Comparator.comparing(enchantment -> enchantment.getKey().toString()))
                .toList();
    }

    public synchronized ToolDefinition createTool(String rawId, Material material, String world)
            throws IOException, InvalidConfigurationException {
        String id = normalizeId(rawId);
        if (!id.matches(ID_PATTERN)) {
            throw new IllegalArgumentException("Tool IDs may only contain lowercase letters, numbers, underscores, and hyphens.");
        }
        if (definitions.containsKey(id)) {
            throw new IllegalArgumentException("A tool with that ID already exists.");
        }
        requireItemMaterial(material);

        mutate(config -> {
            String root = "tools." + id;
            config.set(root + ".enabled", true);
            config.set(root + ".display_name", "<gradient:#4158D0:#C850C0><bold>" + humanize(id) + "</bold></gradient>");
            config.set(root + ".base_material", material.name());
            config.set(root + ".allowed_worlds", List.of(world));
            config.set(root + ".tracking.type", TrackingType.BLOCKS_BROKEN.name());
            config.set(root + ".tracking.targets", List.of());
            config.set(root + ".levels.1.requirement", 500L);
            config.set(root + ".levels.1.enchantments", Map.of());
            config.set(root + ".levels.1.item.unbreakable", false);
            config.set(root + ".levels.1.item.glint", GlintMode.AUTO.name());
            config.set(root + ".levels.1.item.hide_enchantments", false);
            config.set(root + ".levels.1.item.hide_attributes", false);
            config.set(root + ".levels.1.lore", List.of(
                    "<gray>A progressive Plexon tool.</gray>",
                    "<dark_gray>Bound world:</dark_gray> <white>{world}</white>",
                    "<gray>Level <yellow>{level}</yellow>/<yellow>{max_level}</yellow> <dark_gray>•</dark_gray> <aqua>{current}/{required}</aqua></gray>",
                    "{bar}"
            ));
        });
        return definitions.get(id);
    }

    public synchronized void deleteTool(String id) throws IOException, InvalidConfigurationException {
        requireTool(id);
        mutate(config -> config.set("tools." + normalizeId(id), null));
    }

    public synchronized void setEnabled(String id, boolean enabled) throws IOException, InvalidConfigurationException {
        requireTool(id);
        mutate(config -> config.set(path(id, "enabled"), enabled));
    }

    public synchronized void setDisplayName(String id, String displayName)
            throws IOException, InvalidConfigurationException {
        requireTool(id);
        requireNonBlank(displayName, "Display name cannot be blank.");
        mutate(config -> config.set(path(id, "display_name"), displayName));
    }

    public synchronized void setBaseMaterial(String id, Material material)
            throws IOException, InvalidConfigurationException {
        requireTool(id);
        requireItemMaterial(material);
        mutate(config -> config.set(path(id, "base_material"), material.name()));
    }

    public synchronized void toggleWorld(String id, String world)
            throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        List<String> worlds = new ArrayList<>(tool.allowedWorlds());
        Optional<String> existing = worlds.stream().filter(value -> value.equalsIgnoreCase(world)).findFirst();
        if (existing.isPresent()) {
            if (worlds.size() == 1) {
                throw new IllegalArgumentException("A tool must allow at least one world.");
            }
            worlds.remove(existing.get());
        } else {
            worlds.add(world);
        }
        mutate(config -> config.set(path(id, "allowed_worlds"), worlds));
    }

    public synchronized void setTrackingType(String id, TrackingType trackingType)
            throws IOException, InvalidConfigurationException {
        requireTool(id);
        mutate(config -> {
            config.set(path(id, "tracking.type"), trackingType.name());
            config.set(path(id, "tracking.targets"), List.of());
        });
    }

    public synchronized void setTrackingTargets(String id, List<String> targets)
            throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        List<String> normalized = targets.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        validateTargets(tool.trackingType(), normalized, id);
        mutate(config -> config.set(path(id, "tracking.targets"), normalized));
    }

    public synchronized void setLevelRequirement(String id, int level, long requirement)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        if (requirement < 1L) {
            throw new IllegalArgumentException("Requirement must be at least 1.");
        }
        mutate(config -> config.set(levelPath(id, level, "requirement"), requirement));
    }

    public synchronized void setLevelDisplayName(String id, int level, String displayName)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        if (displayName != null) {
            requireNonBlank(displayName, "Level display name cannot be blank.");
        }
        mutate(config -> config.set(levelPath(id, level, "display_name"), displayName));
    }

    public synchronized void setLevelEnchantments(String id, int level, Map<String, Integer> enchantments)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        Map<String, Integer> normalized = normalizeEnchantments(enchantments);
        mutate(config -> config.set(levelPath(id, level, "enchantments"), normalized));
    }

    public synchronized void setLevelEnchantment(String id, int level, String enchantmentName, int enchantmentLevel)
            throws IOException, InvalidConfigurationException {
        ToolLevel toolLevel = requireLevel(id, level);
        Enchantment enchantment = resolveEnchantment(enchantmentName);
        if (enchantment == null) {
            throw new IllegalArgumentException("Unknown enchantment: " + enchantmentName);
        }
        if (enchantmentLevel < 0 || enchantmentLevel > 255) {
            throw new IllegalArgumentException("Enchantment levels must be between 0 and 255.");
        }
        Map<String, Integer> updated = new LinkedHashMap<>();
        toolLevel.enchantments().forEach((key, value) -> updated.put(key.getKey().toString(), value));
        if (enchantmentLevel == 0) {
            updated.remove(enchantment.getKey().toString());
        } else {
            updated.put(enchantment.getKey().toString(), enchantmentLevel);
        }
        setLevelEnchantments(id, level, updated);
    }

    public synchronized void setLevelLore(String id, int level, List<String> lore)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        List<String> normalized = lore.stream().map(String::trim).toList();
        mutate(config -> config.set(levelPath(id, level, "lore"), normalized));
    }

    public synchronized void setLevelMaterial(String id, int level, Material material)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        if (material != null) {
            requireItemMaterial(material);
        }
        mutate(config -> {
            config.set(levelPath(id, level, "material"), material == null ? null : material.name());
            config.set(levelPath(id, level, "material_upgrade"), null);
        });
    }

    public synchronized void setLevelMaterialUpgrade(String id, int level, Material material)
            throws IOException, InvalidConfigurationException {
        setLevelMaterial(id, level, material);
    }

    public synchronized void setLevelUnbreakable(String id, int level, boolean value)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        mutate(config -> config.set(levelPath(id, level, "item.unbreakable"), value));
    }

    public synchronized void setLevelGlint(String id, int level, GlintMode value)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        mutate(config -> config.set(levelPath(id, level, "item.glint"), value.name()));
    }

    public synchronized void setLevelHideEnchantments(String id, int level, boolean value)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        mutate(config -> config.set(levelPath(id, level, "item.hide_enchantments"), value));
    }

    public synchronized void setLevelHideAttributes(String id, int level, boolean value)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        mutate(config -> config.set(levelPath(id, level, "item.hide_attributes"), value));
    }

    public synchronized void setLevelCustomModelData(String id, int level, Integer value)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        if (value != null && value < 0) {
            throw new IllegalArgumentException("Custom model data cannot be negative.");
        }
        mutate(config -> config.set(levelPath(id, level, "item.custom_model_data"), value));
    }

    public synchronized int addLevel(String id) throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        ToolLevel previous = tool.levels().lastEntry().getValue();
        long requirement = previous.requirement() > Long.MAX_VALUE / 2L
                ? Long.MAX_VALUE
                : previous.requirement() * 2L;
        List<ToolLevel> levels = new ArrayList<>(tool.levels().values());
        levels.add(previous.withNumber(levels.size() + 1).withRequirement(requirement));
        mutate(config -> writeLevels(config, id, levels));
        return levels.size();
    }

    public synchronized int duplicateLevel(String id, int sourceLevel)
            throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        ToolLevel source = requireLevel(id, sourceLevel);
        List<ToolLevel> levels = new ArrayList<>(tool.levels().values());
        levels.add(sourceLevel, source);
        renumber(levels);
        mutate(config -> writeLevels(config, id, levels));
        return sourceLevel + 1;
    }

    public synchronized int moveLevel(String id, int level, int direction)
            throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        requireLevel(id, level);
        int destination = level + Integer.signum(direction);
        if (destination < 1 || destination > tool.maxLevel()) {
            throw new IllegalArgumentException("That level cannot be moved farther in that direction.");
        }
        List<ToolLevel> levels = new ArrayList<>(tool.levels().values());
        Collections.swap(levels, level - 1, destination - 1);
        renumber(levels);
        mutate(config -> writeLevels(config, id, levels));
        return destination;
    }

    public synchronized void removeLevel(String id, int level)
            throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        requireLevel(id, level);
        if (tool.levels().size() <= 1) {
            throw new IllegalArgumentException("Every tool must retain at least one level.");
        }
        List<ToolLevel> levels = new ArrayList<>(tool.levels().values());
        levels.remove(level - 1);
        renumber(levels);
        mutate(config -> writeLevels(config, id, levels));
    }

    public synchronized void removeLastLevel(String id) throws IOException, InvalidConfigurationException {
        removeLevel(id, requireTool(id).maxLevel());
    }

    private void mutate(Consumer<YamlConfiguration> mutation) throws IOException, InvalidConfigurationException {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.loadFromString(yaml.saveToString());
        mutation.accept(candidate);
        Map<String, ToolDefinition> parsed = parseDefinitions(candidate, true);
        candidate.save(file);
        yaml = candidate;
        definitions = Collections.unmodifiableMap(parsed);
    }

    private Map<String, ToolDefinition> parseDefinitions(YamlConfiguration config, boolean strict) {
        ConfigurationSection tools = config.getConfigurationSection("tools");
        if (tools == null) {
            return Map.of();
        }

        Map<String, ToolDefinition> parsed = new LinkedHashMap<>();
        for (String rawId : tools.getKeys(false)) {
            String id = normalizeId(rawId);
            try {
                if (!id.matches(ID_PATTERN)) {
                    throw new IllegalArgumentException("invalid ID; expected " + ID_PATTERN);
                }
                parsed.put(id, parseTool(config, rawId, id));
            } catch (RuntimeException exception) {
                if (strict) {
                    throw new IllegalArgumentException("Invalid tool '" + rawId + "': " + exception.getMessage(), exception);
                }
                plugin.getLogger().log(Level.SEVERE,
                        "Skipping invalid tool '" + rawId + "': " + exception.getMessage(), exception);
            }
        }
        return parsed;
    }

    private ToolDefinition parseTool(YamlConfiguration config, String rawId, String id) {
        String root = "tools." + rawId;
        boolean enabled = config.getBoolean(root + ".enabled", true);
        String displayName = requireText(config.getString(root + ".display_name"), "display_name", id);
        Material baseMaterial = parseItemMaterial(config.getString(root + ".base_material"), "base_material", id);

        List<String> worldList = config.getStringList(root + ".allowed_worlds").stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (worldList.isEmpty()) {
            throw new IllegalArgumentException("allowed_worlds cannot be empty");
        }
        Set<String> worlds = new LinkedHashSet<>(worldList);

        TrackingType trackingType = TrackingType.parse(requireText(
                config.getString(root + ".tracking.type"), "tracking.type", id));
        List<String> targetNames = config.getStringList(root + ".tracking.targets");
        validateTargets(trackingType, targetNames, id);
        Set<Material> blockTargets = new LinkedHashSet<>();
        Set<EntityType> entityTargets = new LinkedHashSet<>();
        if (trackingType == TrackingType.BLOCKS_BROKEN) {
            targetNames.forEach(name -> blockTargets.add(Material.matchMaterial(name)));
        } else {
            targetNames.forEach(name -> entityTargets.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT))));
        }

        NavigableMap<Integer, ToolLevel> levels = parseLevels(config, root, id, displayName, baseMaterial);
        return new ToolDefinition(id, enabled, displayName, baseMaterial, worlds, trackingType,
                blockTargets, entityTargets, levels);
    }

    private NavigableMap<Integer, ToolLevel> parseLevels(
            YamlConfiguration config,
            String root,
            String id,
            String baseDisplayName,
            Material baseMaterial
    ) {
        ConfigurationSection levelSection = config.getConfigurationSection(root + ".levels");
        if (levelSection == null || levelSection.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("levels cannot be empty");
        }

        NavigableMap<Integer, String> levelKeys = new TreeMap<>();
        for (String levelKey : levelSection.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(levelKey);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("level keys must be positive integers: " + levelKey);
            }
            if (level < 1) {
                throw new IllegalArgumentException("level numbers must be positive");
            }
            levelKeys.put(level, levelKey);
        }
        if (levelKeys.firstKey() != 1) {
            throw new IllegalArgumentException("levels must begin at 1");
        }
        int expected = 1;
        for (int actual : levelKeys.keySet()) {
            if (actual != expected++) {
                throw new IllegalArgumentException("levels must be contiguous");
            }
        }

        String inheritedDisplayName = baseDisplayName;
        Material inheritedMaterial = baseMaterial;
        NavigableMap<Integer, ToolLevel> levels = new TreeMap<>();
        for (Map.Entry<Integer, String> entry : levelKeys.entrySet()) {
            int level = entry.getKey();
            String levelRoot = root + ".levels." + entry.getValue();
            long requirement = config.getLong(levelRoot + ".requirement", -1L);
            if (requirement < 1L) {
                throw new IllegalArgumentException("level " + level + " requirement must be at least 1");
            }

            String levelDisplayName = config.getString(levelRoot + ".display_name");
            boolean displayNameOverride = levelDisplayName != null && !levelDisplayName.isBlank();
            if (displayNameOverride) {
                inheritedDisplayName = levelDisplayName;
            }

            String materialName = config.getString(levelRoot + ".material");
            if (materialName == null || materialName.isBlank()) {
                materialName = config.getString(levelRoot + ".material_upgrade");
            }
            boolean materialOverride = materialName != null && !materialName.isBlank();
            if (materialOverride) {
                inheritedMaterial = parseItemMaterial(materialName, "level " + level + " material", id);
            }

            Map<Enchantment, Integer> enchantments = parseEnchantments(config, levelRoot, level);
            List<String> lore = config.getStringList(levelRoot + ".lore");
            boolean unbreakable = config.getBoolean(levelRoot + ".item.unbreakable", false);
            GlintMode glint = GlintMode.parse(config.getString(levelRoot + ".item.glint", GlintMode.AUTO.name()));
            boolean hideEnchantments = config.getBoolean(levelRoot + ".item.hide_enchantments", false);
            boolean hideAttributes = config.getBoolean(levelRoot + ".item.hide_attributes", false);
            Integer customModelData = config.contains(levelRoot + ".item.custom_model_data")
                    ? config.getInt(levelRoot + ".item.custom_model_data")
                    : null;
            if (customModelData != null && customModelData < 0) {
                throw new IllegalArgumentException("level " + level + " custom model data cannot be negative");
            }

            levels.put(level, new ToolLevel(level, requirement, inheritedDisplayName, displayNameOverride,
                    enchantments, inheritedMaterial, materialOverride, lore, unbreakable, glint,
                    hideEnchantments, hideAttributes, customModelData));
        }
        return levels;
    }

    private static Map<Enchantment, Integer> parseEnchantments(
            YamlConfiguration config,
            String levelRoot,
            int level
    ) {
        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        ConfigurationSection enchantSection = config.getConfigurationSection(levelRoot + ".enchantments");
        if (enchantSection == null) {
            return enchantments;
        }
        for (String enchantName : enchantSection.getKeys(false)) {
            Enchantment enchantment = resolveEnchantment(enchantName);
            int enchantLevel = enchantSection.getInt(enchantName);
            if (enchantment == null) {
                throw new IllegalArgumentException("unknown enchantment " + enchantName + " at level " + level);
            }
            if (enchantLevel < 1 || enchantLevel > 255) {
                throw new IllegalArgumentException("invalid enchantment level for " + enchantName);
            }
            enchantments.put(enchantment, enchantLevel);
        }
        return enchantments;
    }

    private static void writeLevels(YamlConfiguration config, String id, List<ToolLevel> levels) {
        String root = "tools." + normalizeId(id) + ".levels";
        config.set(root, null);
        for (int index = 0; index < levels.size(); index++) {
            ToolLevel level = levels.get(index).withNumber(index + 1);
            String levelRoot = root + "." + level.number();
            config.set(levelRoot + ".requirement", level.requirement());
            config.set(levelRoot + ".display_name", level.displayNameOverride() ? level.displayName() : null);
            config.set(levelRoot + ".material", level.materialOverride() ? level.material().name() : null);
            Map<String, Integer> enchantments = new LinkedHashMap<>();
            level.enchantments().forEach((enchantment, value) ->
                    enchantments.put(enchantment.getKey().toString(), value));
            config.set(levelRoot + ".enchantments", enchantments);
            config.set(levelRoot + ".item.unbreakable", level.unbreakable());
            config.set(levelRoot + ".item.glint", level.glint().name());
            config.set(levelRoot + ".item.hide_enchantments", level.hideEnchantments());
            config.set(levelRoot + ".item.hide_attributes", level.hideAttributes());
            config.set(levelRoot + ".item.custom_model_data", level.customModelData());
            config.set(levelRoot + ".lore", level.lore());
        }
    }

    private static void renumber(List<ToolLevel> levels) {
        for (int index = 0; index < levels.size(); index++) {
            levels.set(index, levels.get(index).withNumber(index + 1));
        }
    }

    private void validateTargets(TrackingType type, List<String> targets, String id) {
        for (String target : targets) {
            if (type == TrackingType.BLOCKS_BROKEN) {
                Material material = Material.matchMaterial(target);
                if (material == null || !material.isBlock()) {
                    throw new IllegalArgumentException("unknown block target '" + target + "' for " + id);
                }
            } else {
                try {
                    EntityType.valueOf(target.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("unknown entity target '" + target + "' for " + id);
                }
            }
        }
    }

    private static Map<String, Integer> normalizeEnchantments(Map<String, Integer> enchantments) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        enchantments.forEach((name, value) -> {
            Enchantment enchantment = resolveEnchantment(name);
            if (enchantment == null) {
                throw new IllegalArgumentException("Unknown enchantment: " + name);
            }
            if (value == null || value < 1 || value > 255) {
                throw new IllegalArgumentException("Enchantment levels must be between 1 and 255.");
            }
            normalized.put(enchantment.getKey().toString(), value);
        });
        return normalized;
    }

    private static Enchantment resolveEnchantment(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        NamespacedKey key = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        return key == null ? null
                : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }

    private static Material parseItemMaterial(String value, String field, String id) {
        Material material = value == null ? null : Material.matchMaterial(value);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("invalid " + field + " for " + id + ": " + value);
        }
        return material;
    }

    private static void requireItemMaterial(Material material) {
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("That is not a valid item material.");
        }
    }

    private ToolDefinition requireTool(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + id));
    }

    private ToolLevel requireLevel(String id, int level) {
        return requireTool(id).level(level)
                .orElseThrow(() -> new IllegalArgumentException("Unknown level: " + level));
    }

    private static String path(String id, String suffix) {
        return "tools." + normalizeId(id) + "." + suffix;
    }

    private static String levelPath(String id, int level, String suffix) {
        String base = "tools." + normalizeId(id) + ".levels." + level + ".";
        return suffix.isEmpty() ? base : base + suffix;
    }

    private static String normalizeId(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field, String id) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required for " + id);
        }
        return value;
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String humanize(String id) {
        String[] words = id.split("[_-]");
        List<String> output = new ArrayList<>();
        for (String word : words) {
            if (!word.isEmpty()) {
                output.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return String.join(" ", output);
    }
}
