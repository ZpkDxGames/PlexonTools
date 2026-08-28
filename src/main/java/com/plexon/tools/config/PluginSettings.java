package com.plexon.tools.config;

import org.bukkit.configuration.file.FileConfiguration;

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
    private boolean showLockedTools;
    private int showcaseRows;
    private String adminTitle;
    private String levelUpSound;
    private boolean levelUpParticles;

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
        showLockedTools = config.getBoolean("showcase.show-world-locked-tools", true);
        showcaseRows = Math.max(3, Math.min(6, config.getInt("showcase.rows", 6)));
        adminTitle = config.getString("admin-gui.title",
                "<gradient:#4158D0:#C850C0><bold>PlexonTools Editor</bold></gradient>");
        levelUpSound = config.getString("effects.level-up-sound", "ENTITY_PLAYER_LEVELUP");
        levelUpParticles = config.getBoolean("effects.level-up-particles", true);
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
    public boolean showLockedTools() { return showLockedTools; }
    public int showcaseRows() { return showcaseRows; }
    public String adminTitle() { return adminTitle; }
    public String levelUpSound() { return levelUpSound; }
    public boolean levelUpParticles() { return levelUpParticles; }
}
