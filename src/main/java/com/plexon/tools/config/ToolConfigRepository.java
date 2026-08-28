package com.plexon.tools.config;

import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.model.ToolLevel;
import com.plexon.tools.model.TrackingType;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
        Map<String, ToolDefinition> parsed = parseDefinitions(candidate);
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

    public synchronized ToolDefinition createTool(String rawId, Material material, String world)
            throws IOException, InvalidConfigurationException {
        String id = normalizeId(rawId);
        if (!id.matches(ID_PATTERN)) {
            throw new IllegalArgumentException("Tool IDs may only contain lowercase letters, numbers, underscores, and hyphens.");
        }
        if (definitions.containsKey(id)) {
            throw new IllegalArgumentException("A tool with that ID already exists.");
        }
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("Select a valid item material first.");
        }

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
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name cannot be blank.");
        }
        mutate(config -> config.set(path(id, "display_name"), displayName));
    }

    public synchronized void setBaseMaterial(String id, Material material)
            throws IOException, InvalidConfigurationException {
        requireTool(id);
        if (material == null || !material.isItem()) {
            throw new IllegalArgumentException("That is not a valid item material.");
        }
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

    public synchronized void setLevelEnchantments(String id, int level, Map<String, Integer> enchantments)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        Map<String, Integer> normalized = new LinkedHashMap<>();
        enchantments.forEach((name, value) -> {
            String key = name.trim().toUpperCase(Locale.ROOT);
            Enchantment enchantment = resolveEnchantment(key);
            if (enchantment == null) {
                throw new IllegalArgumentException("Unknown enchantment: " + name);
            }
            if (value < 1 || value > 255) {
                throw new IllegalArgumentException("Enchantment levels must be between 1 and 255.");
            }
            normalized.put(key, value);
        });
        mutate(config -> config.set(levelPath(id, level, "enchantments"), normalized));
    }

    public synchronized void setLevelLore(String id, int level, List<String> lore)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        if (lore.isEmpty()) {
            throw new IllegalArgumentException("Lore must contain at least one line.");
        }
        mutate(config -> config.set(levelPath(id, level, "lore"), lore));
    }

    public synchronized void setLevelMaterialUpgrade(String id, int level, Material material)
            throws IOException, InvalidConfigurationException {
        requireLevel(id, level);
        if (material != null && !material.isItem()) {
            throw new IllegalArgumentException("That is not a valid item material.");
        }
        mutate(config -> config.set(levelPath(id, level, "material_upgrade"),
                material == null ? null : material.name()));
    }

    public synchronized int addLevel(String id) throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        ToolLevel previous = tool.levels().lastEntry().getValue();
        int number = previous.number() + 1;
        long requirement = previous.requirement() > Long.MAX_VALUE / 2L
                ? Long.MAX_VALUE
                : previous.requirement() * 2L;

        Map<String, Integer> enchantments = new LinkedHashMap<>();
        previous.enchantments().forEach((enchantment, value) ->
                enchantments.put(enchantment.getKey().getKey().toUpperCase(Locale.ROOT), value));

        mutate(config -> {
            String root = levelPath(id, number, "");
            config.set(root + "requirement", requirement);
            config.set(root + "enchantments", enchantments);
            config.set(root + "lore", previous.lore());
            if (previous.materialUpgrade() != null) {
                config.set(root + "material_upgrade", previous.materialUpgrade().name());
            }
        });
        return number;
    }

    public synchronized void removeLastLevel(String id) throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        if (tool.levels().size() <= 1) {
            throw new IllegalArgumentException("Every tool must retain level 1.");
        }
        int last = tool.levels().lastKey();
        mutate(config -> config.set("tools." + normalizeId(id) + ".levels." + last, null));
    }

    private void mutate(Consumer<YamlConfiguration> mutation) throws IOException, InvalidConfigurationException {
        mutation.accept(yaml);
        yaml.save(file);
        reload();
    }

    private Map<String, ToolDefinition> parseDefinitions(YamlConfiguration config) {
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

        ConfigurationSection levelSection = config.getConfigurationSection(root + ".levels");
        if (levelSection == null || levelSection.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("levels cannot be empty");
        }
        NavigableMap<Integer, ToolLevel> levels = new TreeMap<>();
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
            String levelRoot = root + ".levels." + levelKey;
            long requirement = config.getLong(levelRoot + ".requirement", -1L);
            if (requirement < 1L) {
                throw new IllegalArgumentException("level " + level + " requirement must be at least 1");
            }

            Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
            ConfigurationSection enchantSection = config.getConfigurationSection(levelRoot + ".enchantments");
            if (enchantSection != null) {
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
            }

            String upgradeName = config.getString(levelRoot + ".material_upgrade");
            Material upgrade = upgradeName == null || upgradeName.isBlank()
                    ? null
                    : parseItemMaterial(upgradeName, "material_upgrade", id);
            List<String> lore = config.getStringList(levelRoot + ".lore");
            if (lore.isEmpty()) {
                throw new IllegalArgumentException("level " + level + " lore cannot be empty");
            }
            levels.put(level, new ToolLevel(level, requirement, enchantments, upgrade, lore));
        }

        if (levels.firstKey() != 1) {
            throw new IllegalArgumentException("levels must begin at 1");
        }
        int expected = 1;
        for (int actual : levels.keySet()) {
            if (actual != expected++) {
                throw new IllegalArgumentException("levels must be contiguous");
            }
        }

        return new ToolDefinition(id, enabled, displayName, baseMaterial, worlds, trackingType,
                blockTargets, entityTargets, levels);
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
