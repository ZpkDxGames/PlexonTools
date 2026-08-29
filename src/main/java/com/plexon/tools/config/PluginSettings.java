package com.plexon.tools.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public final class PluginSettings {
    private boolean enforceBoundWorld;
    private boolean enforceOwner;
    private boolean cancelBlockBreaks;
    private boolean cancelInteractions;
    private boolean cancelAttacks;
    private long warningCooldownMillis;
    private long registryAutosaveTicks;
    private int progressBarWidth;
    private String progressFilledSymbol;
    private String progressEmptySymbol;
    private String progressFilledFormat;
    private String progressEmptyFormat;
    private String showcaseTitle;
    private String categoryTitle;
    private boolean showLockedTools;
    private int showcaseRows;
    private String adminTitle;
    private String levelUpSound;
    private boolean levelUpParticles;
    private List<String> defaultLore;
    private String requirementLine;
    private String maximumRequirementLine;
    private boolean worldMenuAutoShowAllowedTools;
    private boolean worldMenuTogglePanelEnabled;
    private boolean worldMenuToolCardActiveGlint;
    private MenuItemTemplate worldMenuToolCard;
    private MenuItemTemplate worldMenuActivePanel;
    private MenuItemTemplate worldMenuInactivePanel;

    public void load(FileConfiguration config) {
        enforceBoundWorld = config.getBoolean("settings.enforce-bound-world", true);
        enforceOwner = config.getBoolean("settings.enforce-owner", true);
        cancelBlockBreaks = config.getBoolean("settings.cancel-unauthorized-block-breaks", true);
        cancelInteractions = config.getBoolean("settings.cancel-unauthorized-interactions", true);
        cancelAttacks = config.getBoolean("settings.cancel-unauthorized-attacks", true);
        warningCooldownMillis = Math.max(250L,
                config.getLong("settings.unauthorized-warning-cooldown-ms", 1500L));
        registryAutosaveTicks = Math.max(200L,
                config.getLong("settings.registry-autosave-ticks", 6000L));

        progressBarWidth = Math.max(5, Math.min(50, config.getInt("progress-bar.width", 20)));
        progressFilledSymbol = config.getString("progress-bar.filled-symbol", "|");
        progressEmptySymbol = config.getString("progress-bar.empty-symbol", "|");
        progressFilledFormat = config.getString("progress-bar.filled-format",
                "<gradient:#41E296:#A8FF78>");
        progressEmptyFormat = config.getString("progress-bar.empty-format", "<dark_gray>");

        showcaseTitle = config.getString("showcase.title",
                "<gradient:#4158D0:#C850C0><bold>PlexonTools</bold></gradient>");
        categoryTitle = config.getString("showcase.category-title",
                "<gradient:#4158D0:#C850C0><bold>Tool Categories</bold></gradient>");
        showLockedTools = config.getBoolean("showcase.show-world-locked-tools", true);
        showcaseRows = Math.max(3, Math.min(6, config.getInt("showcase.rows", 6)));
        adminTitle = config.getString("admin-gui.title",
                "<gradient:#4158D0:#C850C0><bold>PlexonTools Editor</bold></gradient>");
        levelUpSound = config.getString("effects.level-up-sound", "ENTITY_PLAYER_LEVELUP");
        levelUpParticles = config.getBoolean("effects.level-up-particles", true);
        worldMenuAutoShowAllowedTools = config.getBoolean(
                "world-menu.auto-show-allowed-tools", true);
        worldMenuTogglePanelEnabled = config.getBoolean(
                "world-menu.toggle-panel.enabled", true);
        worldMenuToolCardActiveGlint = config.getBoolean(
                "world-menu.tool-card.glint-when-active", true);
        worldMenuToolCard = menuItem(config, "world-menu.tool-card", "TOOL", true,
                "{tool}", List.of(
                        "<dark_gray>{tool_id}</dark_gray>",
                        "",
                        "<gray>Level:</gray> <yellow>{level}/{max_level}</yellow>",
                        "<gray>Progress:</gray> <aqua>{current}</aqua><dark_gray>/</dark_gray><green>{required}</green>",
                        "<gray>Tracks:</gray> <white>{tracking}</white>",
                        "",
                        "<gray>Status:</gray> {status}",
                        "{toggle_hint}"
                ));
        worldMenuActivePanel = menuItem(config, "world-menu.toggle-panel.active",
                "LIME_STAINED_GLASS_PANE", false,
                "<green><bold>✔ ENABLED</bold></green>", List.of(
                        "<gray>{tool} is active in</gray> <white>{world}</white><gray>.</gray>",
                        "<yellow>Click to deactivate and store it.</yellow>"
                ));
        worldMenuInactivePanel = menuItem(config, "world-menu.toggle-panel.inactive",
                "RED_STAINED_GLASS_PANE", false,
                "<red><bold>✘ DISABLED</bold></red>", List.of(
                        "<gray>{tool} is stored for</gray> <white>{world}</white><gray>.</gray>",
                        "<green>Click to activate it.</green>"
                ));
        requirementLine = config.getString("default_lore_format.stats.requirement_line",
                "<dark_gray> •</dark_gray> <white>{requirement_goal}</white> "
                        + "<dark_gray>—</dark_gray> <aqua>{requirement_current}</aqua>"
                        + "<dark_gray>/</dark_gray><green>{requirement_required}</green>");
        maximumRequirementLine = config.getString(
                "default_lore_format.stats.maximum_requirement_line",
                "<dark_gray> •</dark_gray> <green>Maximum level reached</green>");
        defaultLore = modernDefaultLore(config);
        if (defaultLore.isEmpty()) {
            defaultLore = config.getStringList("default-lore-format.lines");
        }
        if (defaultLore.isEmpty()) {
            defaultLore = List.of(
                    "<gradient:#4158D0:#C850C0><bold>⚡ PLEXON TOOL ⚡</bold></gradient>",
                    "<gradient:#8EC5FC:#E0C3FC>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>",
                    "<gray>Level: <gradient:#FF9A8B:#FF6A88><bold>Lvl {level}</bold></gradient>",
                    "<gray>Objectives:</gray>",
                    "{requirement_lines}",
                    "<gray>Progress: <gradient:#00DBDE:#FC00FF>{current}</gradient><dark_gray>/</dark_gray><green>{required}</green> <gray>({percentage}%)</gray>",
                    "{progress_bar}",
                    "<gradient:#8EC5FC:#E0C3FC>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>",
                    "<dark_gray>Category: <gradient:#A8EDEA:#FED6E3>{category_name}</gradient>",
                    "<dark_gray>Authorized World: <gradient:#FEE140:#FA709A>{bound_world}</gradient>",
                    "<dark_gray>Owner: <white>{owner_name}</white>"
            );
        } else {
            defaultLore = List.copyOf(defaultLore);
        }
    }

    public boolean enforceBoundWorld() { return enforceBoundWorld; }
    public boolean enforceOwner() { return enforceOwner; }
    public boolean cancelBlockBreaks() { return cancelBlockBreaks; }
    public boolean cancelInteractions() { return cancelInteractions; }
    public boolean cancelAttacks() { return cancelAttacks; }
    public long warningCooldownMillis() { return warningCooldownMillis; }
    public long registryAutosaveTicks() { return registryAutosaveTicks; }
    public int progressBarWidth() { return progressBarWidth; }
    public String progressFilledSymbol() { return progressFilledSymbol; }
    public String progressEmptySymbol() { return progressEmptySymbol; }
    public String progressFilledFormat() { return progressFilledFormat; }
    public String progressEmptyFormat() { return progressEmptyFormat; }
    public String showcaseTitle() { return showcaseTitle; }
    public String categoryTitle() { return categoryTitle; }
    public boolean showLockedTools() { return showLockedTools; }
    public int showcaseRows() { return showcaseRows; }
    public String adminTitle() { return adminTitle; }
    public String levelUpSound() { return levelUpSound; }
    public boolean levelUpParticles() { return levelUpParticles; }
    public List<String> defaultLore() { return defaultLore; }
    public String requirementLine() { return requirementLine; }
    public String maximumRequirementLine() { return maximumRequirementLine; }
    public boolean worldMenuAutoShowAllowedTools() { return worldMenuAutoShowAllowedTools; }
    public boolean worldMenuTogglePanelEnabled() { return worldMenuTogglePanelEnabled; }
    public boolean worldMenuToolCardActiveGlint() { return worldMenuToolCardActiveGlint; }
    public MenuItemTemplate worldMenuToolCard() { return worldMenuToolCard; }
    public MenuItemTemplate worldMenuActivePanel() { return worldMenuActivePanel; }
    public MenuItemTemplate worldMenuInactivePanel() { return worldMenuInactivePanel; }

    private static List<String> modernDefaultLore(FileConfiguration config) {
        if (!config.isConfigurationSection("default_lore_format")) {
            return List.of();
        }
        return List.of(
                config.getString("default_lore_format.header", ""),
                config.getString("default_lore_format.divider_top", ""),
                config.getString("default_lore_format.stats.level", ""),
                config.getString("default_lore_format.stats.objective_header",
                        "<gray>Objectives:</gray>"),
                "{requirement_lines}",
                config.getString("default_lore_format.stats.progress_text", ""),
                config.getString("default_lore_format.stats.progress_bar", ""),
                config.getString("default_lore_format.divider_bottom", ""),
                config.getString("default_lore_format.footer.category", ""),
                config.getString("default_lore_format.footer.bound_world", ""),
                config.getString("default_lore_format.footer.owner", "")
        ).stream().filter(line -> !line.isEmpty()).toList();
    }

    private static MenuItemTemplate menuItem(
            FileConfiguration config,
            String path,
            String defaultMaterial,
            boolean allowToolMaterial,
            String defaultName,
            List<String> defaultLore
    ) {
        String material = config.getString(path + ".material", defaultMaterial);
        material = material == null ? defaultMaterial : material.trim().toUpperCase(java.util.Locale.ROOT);
        if (!(allowToolMaterial && material.equals("TOOL"))) {
            Material parsed = Material.matchMaterial(material);
            if (parsed == null || !parsed.isItem() || parsed.isAir()) {
                throw new IllegalArgumentException(path + ".material must be an item material"
                        + (allowToolMaterial ? " or TOOL." : "."));
            }
        }
        String displayName = config.getString(path + ".display-name", defaultName);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(path + ".display-name cannot be blank.");
        }
        List<String> lore = config.isList(path + ".lore")
                ? config.getStringList(path + ".lore") : defaultLore;
        return new MenuItemTemplate(material, displayName, List.copyOf(lore));
    }

    public record MenuItemTemplate(
            String material,
            String displayName,
            List<String> lore
    ) {
        public Material resolveMaterial(Material toolMaterial) {
            return material.equals("TOOL")
                    ? toolMaterial : java.util.Objects.requireNonNull(Material.matchMaterial(material));
        }
    }
}
