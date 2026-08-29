package com.plexon.tools.config;

import com.plexon.tools.model.ToolCategory;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class CategoryRepository {
    private static final String ID_PATTERN = "[a-z0-9_-]+";
    private static final int[] PREFERRED_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final JavaPlugin plugin;
    private final File file;
    private volatile Map<String, ToolCategory> categories = Map.of();
    private YamlConfiguration yaml = new YamlConfiguration();

    public CategoryRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "categories.yml");
    }

    public synchronized void reload() throws IOException, InvalidConfigurationException {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(file);
        Map<String, ToolCategory> parsed = parse(candidate, false);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("categories.yml must contain at least one valid category.");
        }
        yaml = candidate;
        categories = Collections.unmodifiableMap(parsed);
    }

    public Collection<ToolCategory> all() {
        return categories.values();
    }

    public List<ToolCategory> sorted() {
        return categories.values().stream()
                .sorted(Comparator.comparingInt(ToolCategory::slot).thenComparing(ToolCategory::id))
                .toList();
    }

    public Optional<ToolCategory> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(categories.get(normalizeId(id)));
    }

    public int size() {
        return categories.size();
    }

    public String defaultCategoryId() {
        return sorted().getFirst().id();
    }

    public synchronized ToolCategory create(String rawId)
            throws IOException, InvalidConfigurationException {
        String id = normalizeId(rawId);
        validateId(id);
        if (categories.containsKey(id)) {
            throw new IllegalArgumentException("A category with that ID already exists.");
        }
        int slot = nextFreeSlot();
        mutate(config -> {
            String root = "categories." + id;
            config.set(root + ".display_name",
                    "<gradient:#4158D0:#C850C0><bold>" + humanize(id) + "</bold></gradient>");
            config.set(root + ".icon", Material.CHEST.name());
            config.set(root + ".slot", slot);
            config.set(root + ".description", List.of("<gray>Custom PlexonTools category.</gray>"));
        });
        return categories.get(id);
    }

    public synchronized void setDisplayName(String id, String displayName)
            throws IOException, InvalidConfigurationException {
        requireCategory(id);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Category display name cannot be blank.");
        }
        mutate(config -> config.set(path(id, "display_name"), displayName));
    }

    public synchronized void setIcon(String id, Material icon)
            throws IOException, InvalidConfigurationException {
        requireCategory(id);
        if (icon == null || !icon.isItem()) {
            throw new IllegalArgumentException("Category icon must be a valid item material.");
        }
        mutate(config -> config.set(path(id, "icon"), icon.name()));
    }

    public synchronized void setSlot(String id, int slot)
            throws IOException, InvalidConfigurationException {
        requireCategory(id);
        if (slot < 0 || slot > 53) {
            throw new IllegalArgumentException("Category slot must be between 0 and 53.");
        }
        boolean occupied = categories.values().stream()
                .anyMatch(category -> !category.id().equalsIgnoreCase(id) && category.slot() == slot);
        if (occupied) {
            throw new IllegalArgumentException("Another category already uses slot " + slot + ".");
        }
        mutate(config -> config.set(path(id, "slot"), slot));
    }

    public synchronized void setDescription(String id, List<String> description)
            throws IOException, InvalidConfigurationException {
        requireCategory(id);
        mutate(config -> config.set(path(id, "description"), List.copyOf(description)));
    }

    private void mutate(Consumer<YamlConfiguration> mutation)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.loadFromString(yaml.saveToString());
        mutation.accept(candidate);
        Map<String, ToolCategory> parsed = parse(candidate, true);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("At least one category is required.");
        }
        candidate.save(file);
        yaml = candidate;
        categories = Collections.unmodifiableMap(parsed);
    }

    private Map<String, ToolCategory> parse(YamlConfiguration config, boolean strict) {
        ConfigurationSection section = config.getConfigurationSection("categories");
        if (section == null) {
            return Map.of();
        }
        Map<String, ToolCategory> parsed = new LinkedHashMap<>();
        Set<Integer> occupiedSlots = new java.util.HashSet<>();
        for (String rawId : section.getKeys(false)) {
            String id = normalizeId(rawId);
            try {
                validateId(id);
                String root = "categories." + rawId;
                String displayName = requireText(config.getString(root + ".display_name"),
                        "display_name", id);
                Material icon = Material.matchMaterial(config.getString(root + ".icon", "CHEST"));
                if (icon == null || !icon.isItem()) {
                    throw new IllegalArgumentException("invalid category icon");
                }
                int slot = config.getInt(root + ".slot", 10);
                if (!occupiedSlots.add(slot)) {
                    throw new IllegalArgumentException("duplicate category slot " + slot);
                }
                List<String> description = new ArrayList<>(config.getStringList(root + ".description"));
                parsed.put(id, new ToolCategory(id, displayName, icon, slot, description));
            } catch (RuntimeException exception) {
                if (strict) {
                    throw new IllegalArgumentException(
                            "Invalid category '" + rawId + "': " + exception.getMessage(), exception);
                }
                plugin.getLogger().log(Level.SEVERE,
                        "Skipping invalid category '" + rawId + "': " + exception.getMessage(), exception);
            }
        }
        return parsed;
    }

    private ToolCategory requireCategory(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown category: " + id));
    }

    private int nextFreeSlot() {
        for (int slot : PREFERRED_SLOTS) {
            boolean used = categories.values().stream().anyMatch(category -> category.slot() == slot);
            if (!used) {
                return slot;
            }
        }
        throw new IllegalArgumentException("No free category GUI slots remain.");
    }

    private static String path(String id, String suffix) {
        return "categories." + normalizeId(id) + "." + suffix;
    }

    private static void validateId(String id) {
        if (!id.matches(ID_PATTERN)) {
            throw new IllegalArgumentException(
                    "Category IDs may only contain lowercase letters, numbers, underscores, and hyphens.");
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field, String id) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required for " + id);
        }
        return value;
    }

    private static String humanize(String id) {
        return java.util.Arrays.stream(id.split("[_-]"))
                .filter(word -> !word.isBlank())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
