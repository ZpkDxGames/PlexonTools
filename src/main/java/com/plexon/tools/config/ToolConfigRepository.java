package com.plexon.tools.config;

import com.plexon.tools.model.GlintMode;
import com.plexon.tools.model.LevelRequirement;
import com.plexon.tools.model.ProgressionScope;
import com.plexon.tools.model.RequirementMode;
import com.plexon.tools.model.AbilityTarget;
import com.plexon.tools.model.ToolAbilitySettings;
import com.plexon.tools.model.ToolAbilityType;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
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
    private static final Set<Material> FARM_TARGETS = Set.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA,
            Material.SWEET_BERRY_BUSH,
            Material.MELON,
            Material.PUMPKIN,
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.BAMBOO,
            Material.KELP
    );
    private static final Set<Material> FISH_TARGETS = Set.of(
            Material.COD,
            Material.SALMON,
            Material.TROPICAL_FISH,
            Material.PUFFERFISH
    );

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final CategoryRepository categories;
    private final File file;
    private volatile Map<String, ToolDefinition> definitions = Map.of();
    private volatile Set<ToolAbilityType> enabledAbilities = Set.of();
    private YamlConfiguration yaml = new YamlConfiguration();

    public ToolConfigRepository(
            JavaPlugin plugin,
            PluginSettings settings,
            CategoryRepository categories
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.categories = categories;
        this.file = new File(plugin.getDataFolder(), "tools.yml");
    }

    public synchronized void reload() throws IOException, InvalidConfigurationException {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(file);
        Map<String, ToolDefinition> parsed = parseDefinitions(candidate, false);
        yaml = candidate;
        enabledAbilities = collectEnabledAbilities(parsed);
        definitions = Collections.unmodifiableMap(parsed);
    }

    public Collection<ToolDefinition> all() {
        return definitions.values();
    }

    public Optional<ToolDefinition> find(String id) {
        return Optional.ofNullable(findCached(id));
    }

    /** Returns the immutable cached definition without allocating an Optional. */
    public ToolDefinition findCached(String id) {
        if (id == null) {
            return null;
        }
        return definitions.get(normalizeId(id));
    }

    public int size() {
        return definitions.size();
    }

    /** Reports whether any enabled definition can use the supplied ability. */
    public boolean hasEnabledAbility(ToolAbilityType type) {
        return enabledAbilities.contains(type);
    }

    public List<Enchantment> enchantmentOptions() {
        Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        return StreamSupport.stream(registry.spliterator(), false)
                .sorted(java.util.Comparator.comparing(enchantment -> enchantment.getKey().toString()))
                .toList();
    }

    public List<String> targetOptions(TrackingType trackingType) {
        return switch (trackingType.targetKind()) {
            case BLOCK -> Arrays.stream(Material.values())
                    .filter(Material::isBlock)
                    .map(Material::name)
                    .sorted()
                    .toList();
            case ENTITY -> Arrays.stream(EntityType.values())
                    .filter(EntityType::isAlive)
                    .map(EntityType::name)
                    .sorted()
                    .toList();
            case CROP -> FARM_TARGETS.stream().map(Material::name).sorted().toList();
            case FISH -> FISH_TARGETS.stream().map(Material::name).sorted().toList();
        };
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
            config.set(root + ".display_name", "<gradient:#FFF176:#FF8F00><bold>" + humanize(id) + "</bold></gradient>");
            config.set(root + ".base_material", material.name());
            config.set(root + ".category", categories.defaultCategoryId());
            config.set(root + ".allowed_worlds", List.of(world));
            config.set(root + ".progression.scope", ProgressionScope.PLAYER.name());
            config.set(root + ".progression.anchor_world", world);
            config.set(root + ".tracking.type", TrackingType.BLOCKS_BROKEN.name());
            config.set(root + ".tracking.mode", RequirementMode.GENERAL.name());
            config.set(root + ".tracking.amount", 500L);
            config.set(root + ".tracking.targets", null);
            config.set(root + ".levels.1.requirement_mode", RequirementMode.GENERAL.name());
            config.set(root + ".levels.1.requirement", 500L);
            config.set(root + ".levels.1.enchantments", Map.of());
            config.set(root + ".levels.1.item.unbreakable", true);
            config.set(root + ".levels.1.item.glint", GlintMode.AUTO.name());
            config.set(root + ".levels.1.item.hide_enchantments", true);
            config.set(root + ".levels.1.item.hide_attributes", true);
            config.set(root + ".levels.1.abilities", null);
            config.set(root + ".levels.1.lore", settings.defaultLore());
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

    public synchronized void setCategory(String id, String categoryId)
            throws IOException, InvalidConfigurationException {
        requireTool(id);
        String normalized = categoryId == null ? "" : categoryId.trim().toLowerCase(Locale.ROOT);
        categories.find(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + categoryId));
        mutate(config -> config.set(path(id, "category"), normalized));
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
        mutate(config -> {
            config.set(path(id, "allowed_worlds"), worlds);
            if (worlds.stream().noneMatch(value -> value.equalsIgnoreCase(
                    tool.progressionAnchorWorld()))) {
                config.set(path(id, "progression.anchor_world"), worlds.getFirst());
            }
        });
    }

    public synchronized void setProgressionScope(String id, ProgressionScope scope)
            throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        ProgressionScope requested = java.util.Objects.requireNonNull(scope, "scope");
        mutate(config -> {
            config.set(path(id, "progression.scope"), requested.name());
            if (!config.contains(path(id, "progression.anchor_world"))) {
                config.set(path(id, "progression.anchor_world"),
                        tool.progressionAnchorWorld());
            }
        });
    }

    public synchronized void setProgressionAnchorWorld(String id, String world)
            throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        String matched = tool.allowedWorlds().stream()
                .filter(candidate -> candidate.equalsIgnoreCase(world))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "The progression anchor must be one of this tool's allowed worlds."));
        mutate(config -> config.set(path(id, "progression.anchor_world"), matched));
    }

    public synchronized void setTrackingType(String id, TrackingType trackingType)
            throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        List<ToolLevel> levels = tool.levels().values().stream()
                .map(level -> level.withRequirement(LevelRequirement.general(
                        Math.max(1L, level.requirement().requiredTotal()))))
                .toList();
        mutate(config -> {
            config.set(path(id, "tracking.type"), trackingType.name());
            config.set(path(id, "tracking.mode"), RequirementMode.GENERAL.name());
            config.set(path(id, "tracking.amount"), levels.getFirst().requirement().amount());
            config.set(path(id, "tracking.targets"), null);
            writeLevels(config, id, levels);
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
        mutate(config -> {
            // Preserve the beta list-target behavior as an explicitly legacy shape.
            config.set(path(id, "tracking.mode"), null);
            config.set(path(id, "tracking.targets"), normalized);
            for (ToolLevel level : tool.levels().values()) {
                config.set(levelPath(id, level.number(), "requirement_mode"), null);
                config.set(levelPath(id, level.number(), "requirement"),
                        level.requirement().requiredTotal());
                config.set(levelPath(id, level.number(), "requirements"), null);
            }
        });
    }

    public synchronized void setLevelRequirement(String id, int level, long requirement)
            throws IOException, InvalidConfigurationException {
        setLevelRequirementAmount(id, level, requirement);
    }

    public synchronized void setLevelRequirementAmount(String id, int level, long requirement)
            throws IOException, InvalidConfigurationException {
        ToolLevel toolLevel = requireLevel(id, level);
        if (toolLevel.requirement().mode() != RequirementMode.GENERAL) {
            throw new IllegalArgumentException("Use target amounts while this level is in SPECIFIC mode.");
        }
        if (requirement < 1L) {
            throw new IllegalArgumentException("Requirement must be at least 1.");
        }
        mutate(config -> writeRequirement(config, id, level,
                toolLevel.requirement().withAmount(requirement)));
    }

    public synchronized void setLevelRequirementMode(String id, int level, RequirementMode mode)
            throws IOException, InvalidConfigurationException {
        ToolLevel toolLevel = requireLevel(id, level);
        mutate(config -> writeRequirement(config, id, level,
                toolLevel.requirement().withMode(mode)));
    }

    public synchronized void setLevelTargetRequirement(
            String id,
            int level,
            String target,
            Long requirement
    ) throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        ToolLevel toolLevel = requireLevel(id, level);
        if (toolLevel.requirement().mode() != RequirementMode.SPECIFIC) {
            throw new IllegalArgumentException("Switch this level to SPECIFIC mode before editing targets.");
        }
        String normalized = LevelRequirement.normalize(target);
        validateTargets(tool.trackingType(), List.of(normalized), id);
        mutate(config -> writeRequirement(config, id, level,
                toolLevel.requirement().withTarget(normalized, requirement)));
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

    public synchronized void setLevelAbilityEnabled(
            String id,
            int level,
            ToolAbilityType type,
            boolean enabled
    ) throws IOException, InvalidConfigurationException {
        ToolLevel toolLevel = requireLevel(id, level);
        Map<ToolAbilityType, ToolAbilitySettings> updated = new LinkedHashMap<>(toolLevel.abilities());
        if (enabled) {
            updated.putIfAbsent(type, ToolAbilitySettings.defaults(type));
        } else {
            updated.remove(type);
        }
        mutate(config -> writeAbilities(config, id, level, updated));
    }

    public synchronized void setLevelAbilityMultiplier(
            String id,
            int level,
            ToolAbilityType type,
            double multiplier
    ) throws IOException, InvalidConfigurationException {
        ToolLevel toolLevel = requireLevel(id, level);
        ToolAbilitySettings ability = toolLevel.abilities().get(type);
        if (ability == null) {
            throw new IllegalArgumentException("Enable " + type.name() + " before configuring it.");
        }
        Map<ToolAbilityType, ToolAbilitySettings> updated = new LinkedHashMap<>(toolLevel.abilities());
        updated.put(type, ability.withMultiplier(multiplier));
        mutate(config -> writeAbilities(config, id, level, updated));
    }

    public synchronized void setLevelPotionAbility(
            String id,
            int level,
            String effect,
            int potionLevel,
            int durationTicks,
            AbilityTarget target
    ) throws IOException, InvalidConfigurationException {
        ToolLevel toolLevel = requireLevel(id, level);
        ToolAbilitySettings ability = toolLevel.abilities().get(ToolAbilityType.MOB_POTION_EFFECT);
        if (ability == null) {
            throw new IllegalArgumentException("Enable MOB_POTION_EFFECT before configuring it.");
        }
        String normalizedEffect = normalizeNamespacedKey(effect);
        NamespacedKey effectKey = NamespacedKey.fromString(normalizedEffect);
        if (effectKey == null || Registry.MOB_EFFECT.get(effectKey) == null) {
            throw new IllegalArgumentException("Unknown potion effect: " + effect);
        }
        Map<ToolAbilityType, ToolAbilitySettings> updated = new LinkedHashMap<>(toolLevel.abilities());
        updated.put(ToolAbilityType.MOB_POTION_EFFECT,
                ability.withPotion(normalizedEffect, potionLevel, durationTicks, target));
        mutate(config -> writeAbilities(config, id, level, updated));
    }

    public synchronized int addLevel(String id) throws IOException, InvalidConfigurationException {
        ToolDefinition tool = requireTool(id);
        ToolLevel previous = tool.levels().lastEntry().getValue();
        LevelRequirement requirement = doubled(previous.requirement());
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
        enabledAbilities = collectEnabledAbilities(parsed);
        definitions = Collections.unmodifiableMap(parsed);
    }

    private static Set<ToolAbilityType> collectEnabledAbilities(
            Map<String, ToolDefinition> definitions
    ) {
        EnumSet<ToolAbilityType> abilities = EnumSet.noneOf(ToolAbilityType.class);
        definitions.values().stream()
                .filter(ToolDefinition::enabled)
                .flatMap(definition -> definition.levels().values().stream())
                .forEach(level -> abilities.addAll(level.abilities().keySet()));
        return abilities.isEmpty() ? Set.of() : Set.copyOf(abilities);
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
        String category = config.getString(root + ".category", categories.defaultCategoryId())
                .trim().toLowerCase(Locale.ROOT);
        if (categories.find(category).isEmpty()) {
            throw new IllegalArgumentException("unknown category '" + category + "'");
        }

        List<String> worldList = config.getStringList(root + ".allowed_worlds").stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (worldList.isEmpty()) {
            throw new IllegalArgumentException("allowed_worlds cannot be empty");
        }
        Set<String> worlds = new LinkedHashSet<>(worldList);
        boolean scopeConfigured = config.contains(root + ".progression.scope");
        ProgressionScope progressionScope = scopeConfigured
                ? ProgressionScope.parse(requireText(config.getString(
                        root + ".progression.scope"), "progression.scope", id))
                : worldList.size() > 1 ? ProgressionScope.PLAYER : ProgressionScope.WORLD;
        String progressionAnchorWorld = config.getString(
                root + ".progression.anchor_world", worldList.getFirst());

        TrackingType trackingType = TrackingType.parse(requireText(
                config.getString(root + ".tracking.type"), "tracking.type", id));
        String trackingRoot = root + ".tracking";
        boolean modeExplicit = config.contains(trackingRoot + ".mode");
        boolean legacyTargetList = config.isList(trackingRoot + ".targets");
        List<String> legacyTargets = legacyTargetList
                ? config.getStringList(trackingRoot + ".targets").stream()
                        .map(LevelRequirement::normalize)
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .toList()
                : List.of();
        Map<String, Long> rootTargetRequirements = parseTargetRequirements(
                config, trackingRoot + ".targets", "tracking.targets");
        RequirementMode defaultMode = modeExplicit
                ? RequirementMode.parse(requireText(config.getString(trackingRoot + ".mode"),
                        "tracking.mode", id))
                : rootTargetRequirements.isEmpty() ? RequirementMode.GENERAL : RequirementMode.SPECIFIC;
        long defaultAmount = config.getLong(trackingRoot + ".amount", 500L);
        if (defaultAmount < 1L) {
            throw new IllegalArgumentException("tracking.amount must be at least 1");
        }
        if (defaultMode == RequirementMode.SPECIFIC
                && rootTargetRequirements.isEmpty()
                && !legacyTargets.isEmpty()) {
            Map<String, Long> converted = new LinkedHashMap<>();
            legacyTargets.forEach(target -> converted.put(target, defaultAmount));
            rootTargetRequirements = converted;
        }
        validateTargets(trackingType, legacyTargets, id);
        validateTargets(trackingType, new ArrayList<>(rootTargetRequirements.keySet()), id);

        List<String> rootLore = lore(config, root + ".lore", settings.defaultLore(), id);

        NavigableMap<Integer, ToolLevel> levels = parseLevels(config, root, id, displayName,
                baseMaterial, trackingType, defaultMode, modeExplicit, defaultAmount,
                legacyTargetList, legacyTargets, rootTargetRequirements, rootLore);
        return new ToolDefinition(id, enabled, displayName, baseMaterial, worlds, category,
                progressionScope, progressionAnchorWorld, trackingType, defaultMode, levels);
    }

    private NavigableMap<Integer, ToolLevel> parseLevels(
            YamlConfiguration config,
            String root,
            String id,
            String baseDisplayName,
            Material baseMaterial,
            TrackingType trackingType,
            RequirementMode defaultMode,
            boolean rootModeExplicit,
            long defaultAmount,
            boolean legacyTargetList,
            List<String> legacyTargets,
            Map<String, Long> rootTargetRequirements,
            List<String> rootLore
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
            boolean levelModeExplicit = config.contains(levelRoot + ".requirement_mode");
            RequirementMode requirementMode = levelModeExplicit
                    ? RequirementMode.parse(requireText(config.getString(levelRoot + ".requirement_mode"),
                            "level " + level + " requirement_mode", id))
                    : defaultMode;
            LevelRequirement requirement;
            if (requirementMode == RequirementMode.GENERAL) {
                long amount = config.getLong(levelRoot + ".requirement", defaultAmount);
                if (amount < 1L) {
                    throw new IllegalArgumentException("level " + level + " requirement must be at least 1");
                }
                boolean preserveLegacyFilter = legacyTargetList
                        && !rootModeExplicit
                        && !levelModeExplicit;
                requirement = preserveLegacyFilter
                        ? LevelRequirement.filtered(amount, legacyTargets)
                        : LevelRequirement.general(amount);
            } else {
                String requirementsPath = levelRoot + ".requirements";
                boolean requirementsExplicit = config.contains(requirementsPath);
                Map<String, Long> targetRequirements = parseTargetRequirements(
                        config, requirementsPath, "level " + level + " requirements");
                if (targetRequirements.isEmpty() && !requirementsExplicit) {
                    targetRequirements = rootTargetRequirements;
                }
                validateTargets(trackingType, new ArrayList<>(targetRequirements.keySet()), id);
                requirement = LevelRequirement.specific(targetRequirements);
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
            validateMaterialCompatibility(id, level, trackingType, inheritedMaterial, requirement);

            Map<Enchantment, Integer> enchantments = parseEnchantments(config, levelRoot, level);
            List<String> lore = lore(config, levelRoot + ".lore", rootLore, id);
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

            Map<ToolAbilityType, ToolAbilitySettings> abilities = parseAbilities(config, levelRoot, level);
            levels.put(level, new ToolLevel(level, requirement, inheritedDisplayName, displayNameOverride,
                    enchantments, inheritedMaterial, materialOverride, lore, unbreakable, glint,
                    hideEnchantments, hideAttributes, customModelData, abilities));
        }
        return levels;
    }

    private static void validateMaterialCompatibility(
            String toolId,
            int level,
            TrackingType trackingType,
            Material toolMaterial,
            LevelRequirement requirement
    ) {
        if (trackingType != TrackingType.BLOCKS_BROKEN
                || requirement.mode() != RequirementMode.SPECIFIC) {
            return;
        }
        for (String target : requirement.targets().keySet()) {
            Material block = Material.matchMaterial(target);
            if (block == null) {
                continue;
            }
            ToolMaterialCompatibility.incompatibilityReason(toolMaterial, block)
                    .ifPresent(reason -> {
                        throw new IllegalArgumentException("level " + level + " of " + toolId
                                + " has an unreachable block objective: " + reason);
                    });
        }
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

    private static Map<ToolAbilityType, ToolAbilitySettings> parseAbilities(
            YamlConfiguration config,
            String levelRoot,
            int level
    ) {
        String path = levelRoot + ".abilities";
        Map<ToolAbilityType, ToolAbilitySettings> abilities = new LinkedHashMap<>();
        if (config.isList(path)) {
            for (String rawType : config.getStringList(path)) {
                ToolAbilityType type = ToolAbilityType.parse(rawType);
                abilities.put(type, ToolAbilitySettings.defaults(type));
            }
            return abilities;
        }

        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return abilities;
        }
        for (String rawType : section.getKeys(false)) {
            ToolAbilityType type;
            try {
                type = ToolAbilityType.parse(rawType);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("unknown ability " + rawType + " at level " + level);
            }
            String abilityPath = path + "." + rawType;
            Object rawValue = config.get(abilityPath);
            if (rawValue instanceof Boolean enabled && !enabled) {
                continue;
            }
            if (config.isConfigurationSection(abilityPath)
                    && config.contains(abilityPath + ".enabled")
                    && !config.getBoolean(abilityPath + ".enabled")) {
                continue;
            }
            double multiplier = config.getDouble(abilityPath + ".multiplier",
                    type == ToolAbilityType.EXP_BOOSTER ? 1.5D : 1.0D);
            String effect = normalizeNamespacedKey(
                    config.getString(abilityPath + ".effect", "minecraft:haste"));
            int potionLevel = config.getInt(abilityPath + ".level", 2);
            int durationTicks = config.getInt(abilityPath + ".duration_ticks", 100);
            AbilityTarget target = AbilityTarget.parse(
                    config.getString(abilityPath + ".target", AbilityTarget.HOLDER.name()));
            if (type == ToolAbilityType.MOB_POTION_EFFECT) {
                NamespacedKey effectKey = NamespacedKey.fromString(effect);
                if (effectKey == null || Registry.MOB_EFFECT.get(effectKey) == null) {
                    throw new IllegalArgumentException(
                            "unknown potion effect " + effect + " at level " + level);
                }
            }
            abilities.put(type, new ToolAbilitySettings(type, multiplier, effect,
                    potionLevel, durationTicks, target));
        }
        return abilities;
    }

    private static Map<String, Long> parseTargetRequirements(
            YamlConfiguration config,
            String path,
            String field
    ) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return Map.of();
        }
        Map<String, Long> targets = new LinkedHashMap<>();
        for (String rawTarget : section.getKeys(false)) {
            String target = LevelRequirement.normalize(rawTarget);
            long amount = section.getLong(rawTarget, -1L);
            if (target.isBlank()) {
                throw new IllegalArgumentException(field + " contains a blank target");
            }
            if (amount < 1L) {
                throw new IllegalArgumentException(field + "." + rawTarget + " must be at least 1");
            }
            targets.put(target, amount);
        }
        return targets;
    }

    private static List<String> lore(
            YamlConfiguration config,
            String path,
            List<String> fallback,
            String id
    ) {
        if (!config.contains(path)) {
            return fallback;
        }
        if (!config.isList(path)) {
            throw new IllegalArgumentException(path + " must be a YAML list for " + id);
        }
        List<?> rawLines = config.getList(path);
        if (rawLines == null) {
            throw new IllegalArgumentException(path + " must be a YAML list for " + id);
        }
        List<String> lines = new ArrayList<>(rawLines.size());
        for (Object rawLine : rawLines) {
            if (!(rawLine instanceof String line)) {
                throw new IllegalArgumentException(
                        path + " may contain only text lines for " + id);
            }
            lines.add(line);
        }
        return List.copyOf(lines);
    }

    private static void writeLevels(YamlConfiguration config, String id, List<ToolLevel> levels) {
        String toolRoot = "tools." + normalizeId(id);
        String root = toolRoot + ".levels";
        String inheritedDisplayName = config.getString(toolRoot + ".display_name", "");
        Material inheritedMaterial = Material.matchMaterial(
                config.getString(toolRoot + ".base_material", "STONE"));
        config.set(root, null);
        for (int index = 0; index < levels.size(); index++) {
            ToolLevel level = levels.get(index).withNumber(index + 1);
            String levelRoot = root + "." + level.number();
            writeRequirement(config, id, level.number(), level.requirement());
            boolean displayNameOverride = !level.displayName().equals(inheritedDisplayName);
            config.set(levelRoot + ".display_name", displayNameOverride ? level.displayName() : null);
            if (displayNameOverride) {
                inheritedDisplayName = level.displayName();
            }
            boolean materialOverride = level.material() != inheritedMaterial;
            config.set(levelRoot + ".material", materialOverride ? level.material().name() : null);
            if (materialOverride) {
                inheritedMaterial = level.material();
            }
            Map<String, Integer> enchantments = new LinkedHashMap<>();
            level.enchantments().forEach((enchantment, value) ->
                    enchantments.put(enchantment.getKey().toString(), value));
            config.set(levelRoot + ".enchantments", enchantments);
            config.set(levelRoot + ".item.unbreakable", level.unbreakable());
            config.set(levelRoot + ".item.glint", level.glint().name());
            config.set(levelRoot + ".item.hide_enchantments", level.hideEnchantments());
            config.set(levelRoot + ".item.hide_attributes", level.hideAttributes());
            config.set(levelRoot + ".item.custom_model_data", level.customModelData());
            writeAbilities(config, id, level.number(), level.abilities());
            config.set(levelRoot + ".lore", level.lore());
        }
    }

    private static void writeAbilities(
            YamlConfiguration config,
            String id,
            int level,
            Map<ToolAbilityType, ToolAbilitySettings> abilities
    ) {
        String root = levelPath(id, level, "abilities");
        config.set(root, null);
        for (ToolAbilitySettings ability : abilities.values()) {
            String path = root + "." + ability.type().name();
            config.set(path + ".enabled", true);
            if (ability.type() == ToolAbilityType.EXP_BOOSTER) {
                config.set(path + ".multiplier", ability.multiplier());
            }
            if (ability.type() == ToolAbilityType.MOB_POTION_EFFECT) {
                config.set(path + ".effect", ability.potionEffect());
                config.set(path + ".level", ability.potionLevel());
                config.set(path + ".duration_ticks", ability.durationTicks());
                config.set(path + ".target", ability.potionTarget().name());
            }
        }
    }

    private static void writeRequirement(
            YamlConfiguration config,
            String id,
            int level,
        LevelRequirement requirement
    ) {
        String root = levelPath(id, level, "");
        if (requirement.isLegacyFilteredGeneral()) {
            config.set(root + "requirement_mode", null);
            config.set(root + "requirement", requirement.amount());
            config.set(root + "requirements", null);
            return;
        }
        config.set(root + "requirement_mode", requirement.mode().name());
        if (requirement.mode() == RequirementMode.GENERAL) {
            config.set(root + "requirement", requirement.amount());
            config.set(root + "requirements", null);
        } else {
            config.set(root + "requirement", null);
            config.set(root + "requirements", requirement.targets());
        }
    }

    private static void renumber(List<ToolLevel> levels) {
        for (int index = 0; index < levels.size(); index++) {
            levels.set(index, levels.get(index).withNumber(index + 1));
        }
    }

    private static LevelRequirement doubled(LevelRequirement requirement) {
        if (requirement.mode() == RequirementMode.GENERAL) {
            long doubled = saturatingMultiplyByTwo(requirement.amount());
            return requirement.isLegacyFilteredGeneral()
                    ? LevelRequirement.filtered(doubled, requirement.targets().keySet())
                    : LevelRequirement.general(doubled);
        }
        Map<String, Long> targets = new LinkedHashMap<>();
        requirement.targets().forEach((target, amount) ->
                targets.put(target, saturatingMultiplyByTwo(amount)));
        return LevelRequirement.specific(targets);
    }

    private static long saturatingMultiplyByTwo(long value) {
        return value > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : value * 2L;
    }

    private void validateTargets(TrackingType type, List<String> targets, String id) {
        for (String target : targets) {
            switch (type.targetKind()) {
                case BLOCK -> {
                    Material material = Material.matchMaterial(target);
                    if (material == null || !material.isBlock()) {
                        throw new IllegalArgumentException(
                                "unknown block target '" + target + "' for " + id);
                    }
                }
                case CROP -> {
                    Material material = Material.matchMaterial(target);
                    if (material == null || !FARM_TARGETS.contains(material)) {
                        throw new IllegalArgumentException(
                                "unknown farm target '" + target + "' for " + id);
                    }
                }
                case FISH -> {
                    Material material = Material.matchMaterial(target);
                    if (material == null || !FISH_TARGETS.contains(material)) {
                        throw new IllegalArgumentException(
                                "unknown fish target '" + target + "' for " + id);
                    }
                }
                case ENTITY -> {
                    try {
                        EntityType entityType = EntityType.valueOf(target.toUpperCase(Locale.ROOT));
                        if (!entityType.isAlive()) {
                            throw new IllegalArgumentException("not a living entity");
                        }
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalArgumentException(
                                "unknown entity target '" + target + "' for " + id);
                    }
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

    private static String normalizeNamespacedKey(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "minecraft:haste";
        }
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
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
