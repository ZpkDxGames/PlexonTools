package com.plexon.tools.config;

import com.plexon.tools.model.WorldToolMenu;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class WorldMenuRepository {
    private static final String ID_PATTERN = "[a-z0-9_-]+";
    private static final String DEFAULT_TITLE =
            "<gradient:#FFF176:#FF8F00><bold>Mining Loadout</bold></gradient> <dark_gray>• {world}</dark_gray>";

    private final JavaPlugin plugin;
    private final File file;
    private volatile Map<String, WorldToolMenu> menus = Map.of();
    private volatile MenuDefaults defaults = new MenuDefaults(
            DEFAULT_TITLE, 3, Material.BLACK_STAINED_GLASS_PANE, " ");
    private YamlConfiguration yaml = new YamlConfiguration();

    public WorldMenuRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "menus.yml");
    }

    public synchronized void reload() throws IOException, InvalidConfigurationException {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(file);
        MenuDefaults parsedDefaults = parseDefaults(candidate);
        Map<String, WorldToolMenu> parsed = parseMenus(candidate, parsedDefaults, false);
        yaml = candidate;
        defaults = parsedDefaults;
        menus = Collections.unmodifiableMap(parsed);
    }

    public Collection<WorldToolMenu> all() {
        return menus.values();
    }

    public List<WorldToolMenu> sorted() {
        return menus.values().stream()
                .sorted(Comparator.comparing(WorldToolMenu::worldName))
                .toList();
    }

    public Optional<WorldToolMenu> find(String worldName) {
        return Optional.ofNullable(menus.get(WorldToolMenu.normalize(worldName)));
    }

    public WorldToolMenu menuFor(String worldName) {
        return find(worldName).orElseGet(() -> new WorldToolMenu(
                WorldToolMenu.normalize(worldName), defaults.title(), defaults.rows(),
                defaults.fillerMaterial(), defaults.fillerName(), Map.of()));
    }

    public int size() {
        return menus.size();
    }

    public synchronized WorldToolMenu ensureWorld(String rawWorld)
            throws IOException, InvalidConfigurationException {
        String world = normalizeWorld(rawWorld);
        WorldToolMenu existing = menus.get(world);
        if (existing != null) {
            return existing;
        }
        mutate(config -> {
            String root = "worlds." + world;
            config.set(root + ".title", defaults.title());
            config.set(root + ".rows", defaults.rows());
            config.set(root + ".filler.material", defaults.fillerMaterial().name());
            config.set(root + ".filler.name", defaults.fillerName());
            config.createSection(root + ".tools");
        });
        return menus.get(world);
    }

    public synchronized void setTitle(String worldName, String title)
            throws IOException, InvalidConfigurationException {
        requireMenu(worldName);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("World menu title cannot be blank.");
        }
        mutate(config -> config.set(path(worldName, "title"), title));
    }

    public synchronized void setRows(String worldName, int rows)
            throws IOException, InvalidConfigurationException {
        requireMenu(worldName);
        if (rows < 3 || rows > 6) {
            throw new IllegalArgumentException("World menu rows must be between 3 and 6.");
        }
        mutate(config -> config.set(path(worldName, "rows"), rows));
    }

    public synchronized void setFillerMaterial(String worldName, Material material)
            throws IOException, InvalidConfigurationException {
        requireMenu(worldName);
        if (material == null || !material.isItem() || material.isAir()) {
            throw new IllegalArgumentException("World menu filler must be an item material.");
        }
        mutate(config -> config.set(path(worldName, "filler.material"), material.name()));
    }

    public synchronized void setFillerName(String worldName, String name)
            throws IOException, InvalidConfigurationException {
        requireMenu(worldName);
        mutate(config -> config.set(path(worldName, "filler.name"), name == null ? " " : name));
    }

    public synchronized boolean toggleTool(String worldName, String toolId)
            throws IOException, InvalidConfigurationException {
        WorldToolMenu menu = requireMenu(worldName);
        String normalizedTool = normalizeTool(toolId);
        boolean adding = !menu.contains(normalizedTool);
        mutate(config -> config.set(path(worldName, "tools." + normalizedTool + ".slot"),
                adding ? nextFreeSlot(menu) : null));
        return adding;
    }

    public synchronized void setToolSlot(String worldName, String toolId, int slot)
            throws IOException, InvalidConfigurationException {
        WorldToolMenu menu = requireMenu(worldName);
        String normalizedTool = normalizeTool(toolId);
        if (!menu.contains(normalizedTool)) {
            throw new IllegalArgumentException("That tool is not pinned in this world menu.");
        }
        if (!WorldToolMenu.isContentSlot(menu.rows(), slot)) {
            throw new IllegalArgumentException(
                    "Use an inner content slot for this " + menu.rows() + "-row menu.");
        }
        boolean occupied = menu.toolSlots().entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(normalizedTool) && entry.getValue() == slot);
        if (occupied) {
            throw new IllegalArgumentException("Another tool already uses slot " + slot + ".");
        }
        mutate(config -> config.set(path(worldName, "tools." + normalizedTool + ".slot"), slot));
    }

    private void mutate(Consumer<YamlConfiguration> mutation)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.loadFromString(yaml.saveToString());
        mutation.accept(candidate);
        MenuDefaults parsedDefaults = parseDefaults(candidate);
        Map<String, WorldToolMenu> parsed = parseMenus(candidate, parsedDefaults, true);
        candidate.save(file);
        yaml = candidate;
        defaults = parsedDefaults;
        menus = Collections.unmodifiableMap(parsed);
    }

    private MenuDefaults parseDefaults(YamlConfiguration config) {
        String title = config.getString("defaults.title", DEFAULT_TITLE);
        int rows = config.getInt("defaults.rows", 3);
        Material filler = Material.matchMaterial(
                config.getString("defaults.filler.material", "BLACK_STAINED_GLASS_PANE"));
        String fillerName = config.getString("defaults.filler.name", " ");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("menus.yml defaults.title cannot be blank.");
        }
        if (rows < 3 || rows > 6) {
            throw new IllegalArgumentException("menus.yml defaults.rows must be between 3 and 6.");
        }
        if (filler == null || !filler.isItem() || filler.isAir()) {
            throw new IllegalArgumentException("menus.yml default filler must be an item material.");
        }
        return new MenuDefaults(title, rows, filler, fillerName == null ? " " : fillerName);
    }

    private Map<String, WorldToolMenu> parseMenus(
            YamlConfiguration config,
            MenuDefaults parsedDefaults,
            boolean strict
    ) {
        ConfigurationSection worlds = config.getConfigurationSection("worlds");
        if (worlds == null) {
            return Map.of();
        }
        Map<String, WorldToolMenu> parsed = new LinkedHashMap<>();
        for (String rawWorld : worlds.getKeys(false)) {
            String world = WorldToolMenu.normalize(rawWorld);
            try {
                validateId(world, "World menu IDs");
                String root = "worlds." + rawWorld;
                String title = config.getString(root + ".title", parsedDefaults.title());
                int rows = config.getInt(root + ".rows", parsedDefaults.rows());
                Material filler = Material.matchMaterial(config.getString(
                        root + ".filler.material", parsedDefaults.fillerMaterial().name()));
                String fillerName = config.getString(root + ".filler.name", parsedDefaults.fillerName());
                Map<String, Integer> slots = new LinkedHashMap<>();
                ConfigurationSection tools = config.getConfigurationSection(root + ".tools");
                if (tools != null) {
                    for (String rawTool : tools.getKeys(false)) {
                        String tool = normalizeTool(rawTool);
                        slots.put(tool, config.getInt(root + ".tools." + rawTool + ".slot", -1));
                    }
                }
                parsed.put(world, new WorldToolMenu(world, title, rows, filler,
                        fillerName, slots));
            } catch (RuntimeException exception) {
                if (strict) {
                    throw new IllegalArgumentException(
                            "Invalid world menu '" + rawWorld + "': " + exception.getMessage(), exception);
                }
                plugin.getLogger().log(Level.SEVERE,
                        "Skipping invalid world menu '" + rawWorld + "': "
                                + exception.getMessage(), exception);
            }
        }
        return parsed;
    }

    private WorldToolMenu requireMenu(String worldName) {
        return find(worldName).orElseThrow(() ->
                new IllegalArgumentException("Unknown world menu: " + worldName));
    }

    private static int nextFreeSlot(WorldToolMenu menu) {
        java.util.Set<Integer> cards = java.util.Set.copyOf(menu.toolSlots().values());
        java.util.Set<Integer> panels = new java.util.LinkedHashSet<>();
        for (int card : cards) {
            int panel = card + 9;
            if (panel < menu.size() && !cards.contains(panel)) {
                panels.add(panel);
            }
        }
        for (int row = 1; row < menu.rows() - 1; row++) {
            for (int column = 1; column < 8; column++) {
                int slot = row * 9 + column;
                int panel = slot + 9;
                if (!cards.contains(slot) && !panels.contains(slot)
                        && panel < menu.size() && !cards.contains(panel)
                        && !panels.contains(panel)) {
                    return slot;
                }
            }
        }
        for (int row = 1; row < menu.rows() - 1; row++) {
            for (int column = 1; column < 8; column++) {
                int slot = row * 9 + column;
                if (!cards.contains(slot) && !panels.contains(slot)) {
                    return slot;
                }
            }
        }
        throw new IllegalArgumentException("No free content slots remain in this world menu.");
    }

    private static String path(String worldName, String suffix) {
        return "worlds." + normalizeWorld(worldName) + "." + suffix;
    }

    private static String normalizeWorld(String value) {
        String normalized = WorldToolMenu.normalize(value);
        validateId(normalized, "World names");
        return normalized;
    }

    private static String normalizeTool(String value) {
        String normalized = WorldToolMenu.normalize(value);
        validateId(normalized, "Tool IDs");
        return normalized;
    }

    private static void validateId(String value, String label) {
        if (!value.matches(ID_PATTERN)) {
            throw new IllegalArgumentException(
                    label + " may only contain letters, numbers, underscores, and hyphens.");
        }
    }

    private record MenuDefaults(
            String title,
            int rows,
            Material fillerMaterial,
            String fillerName
    ) {
    }
}
