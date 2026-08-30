package com.plexon.tools;

import com.plexon.tools.command.PlexonToolsCommand;
import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.config.CategoryRepository;
import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.config.WorldMenuRepository;
import com.plexon.tools.gui.GuiManager;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.listener.ToolProgressListener;
import com.plexon.tools.listener.ToolProtectionListener;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.service.ChatPromptService;
import com.plexon.tools.service.AbilityService;
import com.plexon.tools.service.ProgressionService;
import com.plexon.tools.service.ToolGrantService;
import com.plexon.tools.service.ToolActivationService;
import com.plexon.tools.storage.InstanceRegistry;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class PlexonTools extends JavaPlugin {
    private static final List<String> CONFIGURATION_RESOURCES = List.of(
            "config.yml", "tools.yml", "messages.yml", "categories.yml", "menus.yml");
    private final PluginSettings settings = new PluginSettings();
    private MessageService messages;
    private CategoryRepository categories;
    private ToolConfigRepository tools;
    private WorldMenuRepository worldMenus;
    private InstanceRegistry instanceRegistry;
    private ToolItemService itemService;
    private ChatPromptService prompts;
    private AbilityService abilities;
    private ProgressionService progression;
    private ToolActivationService activations;
    private BukkitTask registrySaveTask;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            saveBundledResource("tools.yml");
            saveBundledResource("messages.yml");
            saveBundledResource("categories.yml");
            saveBundledResource("menus.yml");
            refreshConfigurationReferences();

            settings.load(getConfig());
            messages = new MessageService(this);
            messages.reload();
            categories = new CategoryRepository(this);
            categories.reload();
            tools = new ToolConfigRepository(this, settings, categories);
            tools.reload();
            worldMenus = new WorldMenuRepository(this);
            worldMenus.reload();
            instanceRegistry = new InstanceRegistry(this, settings);
            instanceRegistry.load();

            itemService = new ToolItemService(this, messages, settings, categories);
            progression = new ProgressionService(
                    this, itemService, instanceRegistry, settings, messages);
            abilities = new AbilityService(this, tools, progression);
            ToolGrantService grants = new ToolGrantService(itemService, instanceRegistry, messages);
            activations = new ToolActivationService(
                    tools, worldMenus, settings, itemService, instanceRegistry, messages);
            prompts = new ChatPromptService(this, messages);
            GuiManager gui = new GuiManager(this, categories, tools, worldMenus, itemService,
                    activations, grants, prompts, settings, messages);

            getServer().getPluginManager().registerEvents(
                    new ToolProgressListener(tools, itemService, progression, abilities, settings), this);
            getServer().getPluginManager().registerEvents(progression, this);
            getServer().getPluginManager().registerEvents(abilities, this);
            getServer().getPluginManager().registerEvents(
                    new ToolProtectionListener(this, itemService, activations), this);
            getServer().getPluginManager().registerEvents(prompts, this);
            getServer().getPluginManager().registerEvents(gui, this);

            PluginCommand command = Objects.requireNonNull(getCommand("plexontools"),
                    "plexontools command is missing from plugin.yml");
            PlexonToolsCommand executor = new PlexonToolsCommand(
                    categories, tools, grants, gui, messages, this::reloadPlugin,
                    instanceRegistry::createBackup);
            command.setExecutor(executor);
            command.setTabCompleter(executor);

            scheduleRegistrySave();
            progression.start();
            abilities.start();
            getServer().getScheduler().runTask(this,
                    () -> getServer().getOnlinePlayers().forEach(activations::reconcile));
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().info("PlexonTools " + getPluginMeta().getVersion() + " enabled");
            getLogger().info("Loaded tools: " + tools.size());
            getLogger().info("Loaded categories: " + categories.size());
            getLogger().info("Loaded world menus: " + worldMenus.size());
            getLogger().info("Tracked instances: " + instanceRegistry.size());
            getLogger().info("Runtime database: " + instanceRegistry.databaseFile().getFileName());
            getLogger().info("Creator: Tonim (ZpkDxGames)");
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "PlexonTools could not start safely", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (prompts != null) {
            prompts.cancelAll();
        }
        if (abilities != null) {
            abilities.stop();
        }
        if (progression != null) {
            progression.shutdown();
        }
        if (registrySaveTask != null) {
            registrySaveTask.cancel();
        }
        if (instanceRegistry != null) {
            try {
                instanceRegistry.shutdown();
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE,
                        "Could not drain and close the tool instance database", exception);
            }
        }
    }

    private void reloadPlugin() throws Exception {
        progression.pause();
        try {
            reloadConfig();
            settings.load(getConfig());
            messages.reload();
            categories.reload();
            tools.reload();
            itemService.clearDefinitionCaches();
            progression.clearDefinitionCaches();
            worldMenus.reload();
            getServer().getOnlinePlayers().forEach(activations::reconcile);
            scheduleRegistrySave();
        } finally {
            progression.start();
        }
    }

    private void scheduleRegistrySave() {
        if (registrySaveTask != null) {
            registrySaveTask.cancel();
        }
        registrySaveTask = getServer().getScheduler().runTaskTimerAsynchronously(
                this,
                instanceRegistry::flushAsync,
                settings.databaseFlushIntervalTicks(),
                settings.databaseFlushIntervalTicks()
        );
    }

    private void saveBundledResource(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    private void refreshConfigurationReferences() throws IOException {
        java.nio.file.Path directory = getDataFolder().toPath().resolve("examples");
        Files.createDirectories(directory);
        for (String name : CONFIGURATION_RESOURCES) {
            try (InputStream resource = Objects.requireNonNull(getResource(name),
                    "Missing bundled resource " + name)) {
                Files.copy(resource, directory.resolve(name),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
