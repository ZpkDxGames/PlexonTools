package com.plexon.tools.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public final class PluginSettings {
    private boolean enforceBoundWorld;
    private boolean enforceOwner;
    private boolean cancelBlockBreaks;
    private boolean cancelInteractions;
    private boolean cancelAttacks;
    private long warningCooldownMillis;
    private String databaseFile;
    private long databaseFlushIntervalTicks;
    private int databaseWriteBatchSize;
    private int databaseMaxPendingWrites;
    private int databaseBusyTimeoutMillis;
    private int databaseWalAutoCheckpointPages;
    private boolean databaseIntegrityCheck;
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
    private boolean progressActionBar;
    private List<String> defaultLore;
    private String generalRequirementLine;
    private String specificRequirementLine;
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

        databaseFile = databaseFile(config.getString("storage.database-file", "plexontools.db"));
        databaseFlushIntervalTicks = Math.max(1L, Math.min(72000L,
                config.getLong("storage.flush-interval-ticks", 20L)));
        databaseWriteBatchSize = Math.max(1, Math.min(4096,
                config.getInt("storage.write-batch-size", 256)));
        databaseMaxPendingWrites = Math.max(databaseWriteBatchSize, Math.min(100000,
                config.getInt("storage.max-pending-writes", 8192)));
        databaseBusyTimeoutMillis = Math.max(250, Math.min(60000,
                config.getInt("storage.busy-timeout-ms", 5000)));
        databaseWalAutoCheckpointPages = Math.max(1, Math.min(100000,
                config.getInt("storage.wal-autocheckpoint-pages", 1000)));
        databaseIntegrityCheck = config.getBoolean("storage.integrity-check-on-startup", true);

        progressBarWidth = Math.max(5, Math.min(50, config.getInt("progress-bar.width", 20)));
        progressFilledSymbol = config.getString("progress-bar.filled-symbol", "■");
        progressEmptySymbol = config.getString("progress-bar.empty-symbol", "■");
        progressFilledFormat = config.getString("progress-bar.filled-format",
                "<!italic><gradient:#FFF59D:#FFB300:#FF6F00>");
        progressEmptyFormat = config.getString("progress-bar.empty-format", "<!italic><#303030>");

        showcaseTitle = config.getString("showcase.title",
                "<gradient:#FFF176:#FF8F00><bold>Tool Armory</bold></gradient>");
        categoryTitle = config.getString("showcase.category-title",
                "<gradient:#FFF176:#FF8F00><bold>Tool Categories</bold></gradient>");
        showLockedTools = config.getBoolean("showcase.show-world-locked-tools", true);
        showcaseRows = Math.max(3, Math.min(6, config.getInt("showcase.rows", 6)));
        adminTitle = config.getString("admin-gui.title",
                "<gradient:#FFF176:#FF8F00><bold>Tool Manager</bold></gradient>");
        levelUpSound = config.getString("effects.level-up-sound", "ENTITY_PLAYER_LEVELUP");
        levelUpParticles = config.getBoolean("effects.level-up-particles", true);
        progressActionBar = config.getBoolean("effects.progress-action-bar", true);
        worldMenuAutoShowAllowedTools = config.getBoolean(
                "world-menu.auto-show-allowed-tools", true);
        worldMenuTogglePanelEnabled = config.getBoolean(
                "world-menu.toggle-panel.enabled", true);
        worldMenuToolCardActiveGlint = config.getBoolean(
                "world-menu.tool-card.glint-when-active", true);
        worldMenuToolCard = menuItem(config, "world-menu.tool-card", "TOOL", true,
                "{tool}", List.of(
                        "<dark_gray>Bound mining relic</dark_gray>",
                        "",
                        "<#FFB300>⛏</#FFB300>  <gray>Level</gray>  <white><bold>{level}</bold></white><dark_gray> / {max_level}</dark_gray>",
                        "<#FF8F00>◆</#FF8F00>  <gray>Objective</gray>  <white>{tracking}</white>",
                        "<#FFD54F>⚡</#FFD54F>  <gray>Progress</gray>  <white>{current}</white><dark_gray> / </dark_gray><#8BC34A>{required}</#8BC34A>",
                        "",
                        "<gray>Status</gray>  {status}",
                        "{toggle_hint}"
                ));
        worldMenuActivePanel = menuItem(config, "world-menu.toggle-panel.active",
                "LIME_STAINED_GLASS_PANE", false,
                "<gradient:#43A047:#9CCC65><bold>✔ TOOL ACTIVE</bold></gradient>", List.of(
                        "<gray>Equipped for</gray>  <white>{world}</white>",
                        "",
                        "<#FFD54F>Click to store it safely.</#FFD54F>"
                ));
        worldMenuInactivePanel = menuItem(config, "world-menu.toggle-panel.inactive",
                "RED_STAINED_GLASS_PANE", false,
                "<gradient:#E53935:#FF7043><bold>✘ TOOL STORED</bold></gradient>", List.of(
                        "<gray>Stored for</gray>  <white>{world}</white>",
                        "",
                        "<#9CCC65>Click to equip it.</#9CCC65>"
                ));
        LoreSettings lore = loreSettings(config);
        defaultLore = lore.template();
        generalRequirementLine = lore.generalRequirementLine();
        specificRequirementLine = lore.specificRequirementLine();
        maximumRequirementLine = lore.maximumRequirementLine();
    }

    public boolean enforceBoundWorld() { return enforceBoundWorld; }
    public boolean enforceOwner() { return enforceOwner; }
    public boolean cancelBlockBreaks() { return cancelBlockBreaks; }
    public boolean cancelInteractions() { return cancelInteractions; }
    public boolean cancelAttacks() { return cancelAttacks; }
    public long warningCooldownMillis() { return warningCooldownMillis; }
    public String databaseFile() { return databaseFile; }
    public long databaseFlushIntervalTicks() { return databaseFlushIntervalTicks; }
    public int databaseWriteBatchSize() { return databaseWriteBatchSize; }
    public int databaseMaxPendingWrites() { return databaseMaxPendingWrites; }
    public int databaseBusyTimeoutMillis() { return databaseBusyTimeoutMillis; }
    public int databaseWalAutoCheckpointPages() { return databaseWalAutoCheckpointPages; }
    public boolean databaseIntegrityCheck() { return databaseIntegrityCheck; }
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
    public boolean progressActionBar() { return progressActionBar; }
    public List<String> defaultLore() { return defaultLore; }
    public String generalRequirementLine() { return generalRequirementLine; }
    public String specificRequirementLine() { return specificRequirementLine; }
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
                        "<!italic><dark_gray>├─</dark_gray> <gradient:#FFE082:#FFB300><bold>UPGRADE OBJECTIVES</bold></gradient>"),
                "{requirement_lines}",
                config.getString("default_lore_format.stats.progress_text", ""),
                config.getString("default_lore_format.stats.progress_bar", ""),
                config.getString("default_lore_format.divider_bottom", ""),
                config.getString("default_lore_format.footer.category", ""),
                config.getString("default_lore_format.footer.bound_world", ""),
                config.getString("default_lore_format.footer.owner", "")
        ).stream().filter(line -> !line.isEmpty()).toList();
    }

    static LoreSettings loreSettings(FileConfiguration config) {
        String legacyRequirementLine = config.getString(
                "default_lore_format.stats.requirement_line",
                "<!italic><dark_gray>│</dark_gray>  <#FFB300>◆</#FFB300>  "
                        + "<gray>{requirement_goal}</gray>  <dark_gray>•</dark_gray>  "
                        + "<white>{requirement_current}</white><dark_gray>/</dark_gray>"
                        + "<#AEEA00>{requirement_required}</#AEEA00>");
        String generalLine = config.getString(
                "tool-lore.requirements.general-line", legacyRequirementLine);
        String specificLine = config.getString(
                "tool-lore.requirements.specific-line", legacyRequirementLine);
        String maximumLine = config.getString("tool-lore.requirements.maximum-line",
                config.getString("default_lore_format.stats.maximum_requirement_line",
                        "<!italic><dark_gray>│</dark_gray>  <#AEEA00>✦</#AEEA00>  "
                                + "<gradient:#8BC34A:#DCEDC8><bold>"
                                + "FULLY MASTERED</bold></gradient>"));

        boolean enabled = config.getBoolean("tool-lore.enabled", true);
        boolean templateConfigured = config.contains("tool-lore.template");
        if (templateConfigured && !config.isList("tool-lore.template")) {
            throw new IllegalArgumentException("tool-lore.template must be a YAML list.");
        }
        List<String> template;
        if (!enabled) {
            template = List.of();
        } else if (templateConfigured) {
            template = stringList(config, "tool-lore.template");
        } else {
            template = modernDefaultLore(config);
        }
        if (enabled && template.isEmpty()) {
            template = config.getStringList("default-lore-format.lines");
        }
        if (enabled && template.isEmpty()) {
            template = List.of(
                    "<!italic><gradient:#FFF59D:#FFB300><bold>✦  LEGENDARY MINING RELIC  ✦</bold></gradient>",
                    "<!italic><dark_gray>┌</dark_gray><gradient:#FFF59D:#FF8F00>──────────────────────</gradient><dark_gray>┐</dark_gray>",
                    "<!italic><dark_gray>│</dark_gray>  <#FFB300>⛏</#FFB300>  <gray>LEVEL</gray>  <white><bold>{level}</bold></white><dark_gray>/</dark_gray><gray>{max_level}</gray>",
                    "<!italic><dark_gray>├─</dark_gray> <gradient:#FFE082:#FFB300><bold>UPGRADE OBJECTIVES</bold></gradient>",
                    "{requirement_lines}",
                    "<!italic><dark_gray>├─</dark_gray> <gradient:#FFE082:#FFB300><bold>LEVEL MASTERY</bold></gradient>  <dark_gray>•</dark_gray>  <white>{percentage}%</white>",
                    "<!italic><dark_gray>│</dark_gray>  {progress_bar}",
                    "<!italic><dark_gray>└</dark_gray><gradient:#FF8F00:#FFF59D>──────────────────────</gradient><dark_gray>┘</dark_gray>",
                    "<!italic><#FFD54F>👤</#FFD54F>  <dark_gray>SOULBOUND</dark_gray>  <#FFB300>•</#FFB300>  <white>{owner_name}</white>"
            );
        }
        return new LoreSettings(List.copyOf(template), generalLine, specificLine, maximumLine);
    }

    private static List<String> stringList(FileConfiguration config, String path) {
        List<?> raw = config.getList(path);
        if (raw == null) {
            throw new IllegalArgumentException(path + " must be a YAML list.");
        }
        List<String> values = new ArrayList<>(raw.size());
        for (Object value : raw) {
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException(path + " may contain only text lines.");
            }
            values.add(text);
        }
        return List.copyOf(values);
    }

    record LoreSettings(
            List<String> template,
            String generalRequirementLine,
            String specificRequirementLine,
            String maximumRequirementLine
    ) {
    }

    private static String databaseFile(String configured) {
        String name = configured == null ? "" : configured.trim();
        if (name.isBlank() || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..")
                || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".db")) {
            throw new IllegalArgumentException(
                    "storage.database-file must be a simple .db filename inside the plugin folder.");
        }
        return name;
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
