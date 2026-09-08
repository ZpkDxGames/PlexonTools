package com.plexon.tools;

import com.plexon.tools.api.DefaultPlexonToolsAPI;
import com.plexon.tools.api.PlexonToolsAPI;
import com.plexon.tools.command.PlexonToolsCommand;
import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.config.CategoryRepository;
import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.config.WorldMenuRepository;
import com.plexon.tools.gui.GuiManager;
import com.plexon.tools.integration.core.CoreBridge;
import com.plexon.tools.integration.core.CoreBridgeFactory;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.listener.ToolProgressListener;
import com.plexon.tools.listener.ToolProtectionListener;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.service.ChatPromptService;
import com.plexon.tools.service.AbilityService;
import com.plexon.tools.service.ProgressionService;
import com.plexon.tools.service.NaturalBlockTracker;
import com.plexon.tools.service.ToolGrantService;
import com.plexon.tools.service.ToolActivationService;
import com.plexon.tools.storage.InstanceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
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
    private ToolGrantService grants;
    private GuiManager gui;
    private PlexonToolsAPI publicApi;
    private CoreBridge coreBridge;
    private ChatPromptService prompts;
    private AbilityService abilities;
    private ProgressionService progression;
    private NaturalBlockTracker naturalBlocks;
    private ToolActivationService activations;
    private ToolProgressListener progressListener;
    private BukkitTask registrySaveTask;

    @Override
    public void onEnable() {
        coreBridge = CoreBridgeFactory.resolve(this);
        coreBridge.registerStarting();
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
            naturalBlocks = new NaturalBlockTracker(this, settings, instanceRegistry);

            itemService = new ToolItemService(this, messages, settings, categories);
            progression = new ProgressionService(
                    this, itemService, instanceRegistry, settings, messages);
            abilities = new AbilityService(this, tools, progression);
            grants = new ToolGrantService(itemService, instanceRegistry, messages);
            activations = new ToolActivationService(
                    tools, worldMenus, settings, itemService, instanceRegistry, messages);
            prompts = new ChatPromptService(this, messages);
            gui = new GuiManager(this, categories, tools, worldMenus, itemService,
                    activations, grants, prompts, settings, messages);
            progressListener = new ToolProgressListener(
                    tools, progression, abilities, naturalBlocks, settings);

            getServer().getPluginManager().registerEvents(progressListener, this);
            getServer().getPluginManager().registerEvents(progression, this);
            getServer().getPluginManager().registerEvents(abilities, this);
            getServer().getPluginManager().registerEvents(
                    new ToolProtectionListener(this, itemService, activations), this);
            getServer().getPluginManager().registerEvents(prompts, this);
            getServer().getPluginManager().registerEvents(gui, this);
            getServer().getPluginManager().registerEvents(naturalBlocks, this);

            PluginCommand command = Objects.requireNonNull(getCommand("plexontools"),
                    "plexontools command is missing from plugin.yml");
            PlexonToolsCommand executor = new PlexonToolsCommand(
                    categories, tools, grants, gui, messages, this::reloadPlugin,
                    instanceRegistry::createBackup, this::diagnosticsLines);
            command.setExecutor(executor);
            command.setTabCompleter(executor);

            registerPublicApi();
            scheduleRegistrySave();
            naturalBlocks.start();
            progression.start();
            abilities.start();
            getServer().getScheduler().runTask(this,
                    () -> getServer().getOnlinePlayers().forEach(activations::reconcile));
            coreBridge.markReady("Tools, SQLite, public API and progression events ready");
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().info("PlexonTools " + getPluginMeta().getVersion() + " enabled");
            getLogger().info("Loaded tools: " + tools.size());
            getLogger().info("Loaded categories: " + categories.size());
            getLogger().info("Loaded world menus: " + worldMenus.size());
            getLogger().info("Tracked instances: " + instanceRegistry.size());
            getLogger().info("Runtime database: " + instanceRegistry.databaseFile().getFileName());
            getLogger().info("Natural-block progression: "
                    + (settings.naturalBlockProgressionEnabled() ? "enabled" : "disabled"));
            getLogger().info("PlexonCore mode: " + coreBridge.mode()
                    + " (" + coreBridge.registrationState() + ")");
            getLogger().info("Public Tools API: registered");
            getLogger().info("Creator: Tonim (ZpkDxGames)");
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } catch (Exception exception) {
            if (coreBridge != null) {
                coreBridge.markFailed("PlexonTools startup failed: "
                        + exception.getClass().getSimpleName());
            }
            getLogger().log(Level.SEVERE, "PlexonTools could not start safely", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (progressListener != null) {
            progressListener.invalidateAllActiveContexts();
        }
        if (gui != null) {
            gui.shutdown();
        }
        unregisterPublicApi();
        if (prompts != null) {
            prompts.cancelAll();
        }
        if (abilities != null) {
            abilities.stop();
        }
        if (progression != null) {
            progression.shutdown();
        }
        if (naturalBlocks != null) {
            naturalBlocks.stop();
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
        if (coreBridge != null) {
            coreBridge.unregister();
        }
    }

    private void reloadPlugin() throws Exception {
        progression.pause();
        if (progressListener != null) {
            progressListener.invalidateAllActiveContexts();
        }
        try {
            reloadConfig();
            settings.load(getConfig());
            messages.reload();
            categories.reload();
            tools.reload();
            itemService.clearDefinitionCaches();
            progression.clearDefinitionCaches();
            worldMenus.reload();
            naturalBlocks.start();
            getServer().getOnlinePlayers().forEach(activations::reconcile);
            scheduleRegistrySave();
            coreBridge.markReady("PlexonTools reloaded; API and progression services ready");
        } catch (Exception exception) {
            coreBridge.markDegraded("PlexonTools reload failed: "
                    + exception.getClass().getSimpleName());
            throw exception;
        } finally {
            progression.start();
        }
    }

    private void registerPublicApi() {
        publicApi = new DefaultPlexonToolsAPI(tools, itemService, instanceRegistry);
        getServer().getServicesManager().register(
                PlexonToolsAPI.class, publicApi, this, ServicePriority.Normal);
    }

    private void unregisterPublicApi() {
        if (publicApi == null) {
            return;
        }
        getServer().getServicesManager().unregister(PlexonToolsAPI.class, publicApi);
        publicApi = null;
    }

    private List<String> diagnosticsLines() {
        String apiState = publicApi != null
                && getServer().getServicesManager().getRegistrations(PlexonToolsAPI.class).stream()
                .anyMatch(registration -> registration.getProvider() == publicApi)
                ? "REGISTERED" : "UNAVAILABLE";
        String eventState = publicEventsAvailable() ? "AVAILABLE" : "UNAVAILABLE";
        return List.of(
                "<gradient:#66BB6A:#42A5F5><bold>PlexonTools Diagnostics</bold></gradient>",
                diagnostic("Plugin", getPluginMeta().getVersion()),
                diagnostic("Paper", Bukkit.getVersion()),
                diagnostic("Java", System.getProperty("java.version", "unknown")),
                diagnostic("Mode", coreBridge.mode()),
                diagnostic("Core plugin", coreBridge.pluginVersion()),
                diagnostic("Core API", coreBridge.apiVersion() + " (supports "
                        + CoreBridge.SUPPORTED_API_RANGE + ")"),
                diagnostic("Module", coreBridge.registrationState()),
                diagnostic("Module detail", coreBridge.detail()),
                diagnostic("SQLite", instanceRegistry.databaseFile().getFileName()
                        + " • journal=" + instanceRegistry.journalMode().toUpperCase(java.util.Locale.ROOT)),
                diagnostic("Definitions", tools.size() + " tools • " + categories.size()
                        + " categories • " + worldMenus.size() + " world menus"),
                diagnostic("Instances", instanceRegistry.size() + " tracked • "
                        + instanceRegistry.pendingWriteCount() + " pending writes"),
                diagnostic("Natural blocks", settings.naturalBlockProgressionEnabled()
                        ? "ENABLED" : "DISABLED"),
                diagnostic("Public API", apiState),
                diagnostic("Public events", eventState));
    }

    private String diagnostic(String label, String value) {
        return "<dark_gray>•</dark_gray> <gray>" + messages.plain(label) + ":</gray> <white>"
                + messages.plain(value) + "</white>";
    }

    private static boolean publicEventsAvailable() {
        try {
            Class.forName("com.plexon.tools.event.PlexonToolProgressEvent");
            Class.forName("com.plexon.tools.event.PlexonToolLevelUpEvent");
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
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
