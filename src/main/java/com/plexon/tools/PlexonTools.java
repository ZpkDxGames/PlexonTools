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
import com.plexon.tools.performance.MiningPerformanceProfiler;
import com.plexon.tools.service.ChatPromptService;
import com.plexon.tools.service.AbilityService;
import com.plexon.tools.service.ProgressionService;
import com.plexon.tools.service.NaturalBlockTracker;
import com.plexon.tools.service.ToolGrantService;
import com.plexon.tools.service.ToolActivationService;
import com.plexon.tools.storage.InstanceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public final class PlexonTools extends JavaPlugin {
    private static final List<String> CONFIGURATION_RESOURCES = List.of(
            "config.yml", "tools.yml", "messages.yml", "categories.yml", "menus.yml");
    private final PluginSettings settings = new PluginSettings();
    private final MiningPerformanceProfiler miningProfiler = new MiningPerformanceProfiler();
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
    private long lastReloadEpochMillis;
    private String lastReloadState = "STARTUP";

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
                    this, itemService, instanceRegistry, settings, messages, miningProfiler);
            abilities = new AbilityService(this, tools, progression, miningProfiler);
            grants = new ToolGrantService(itemService, instanceRegistry, messages);
            activations = new ToolActivationService(
                    tools, worldMenus, settings, itemService, instanceRegistry, messages);
            prompts = new ChatPromptService(this, messages);
            gui = new GuiManager(this, categories, tools, worldMenus, itemService,
                    activations, grants, prompts, settings, messages);
            progressListener = new ToolProgressListener(
                    tools, progression, abilities, naturalBlocks, settings, miningProfiler);
            miningProfiler.setActiveContextSupplier(progressListener::activeContextCount);

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
                    instanceRegistry::createBackup, this::diagnosticsLines, miningProfiler);
            command.setExecutor(executor);
            command.setTabCompleter(executor);

            registerPublicApi();
            scheduleRegistrySave();
            naturalBlocks.start();
            progression.start();
            abilities.start();
            getServer().getScheduler().runTask(this,
                    () -> getServer().getOnlinePlayers().forEach(activations::reconcile));
            lastReloadEpochMillis = System.currentTimeMillis();
            lastReloadState = "STARTUP";
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
            getLogger().info("Mining authority: LOCAL; PlexonCore is lifecycle/integration only");
            getLogger().info("Mining profiler: disabled by default (/pt perf start)");
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
        miningProfiler.stopSession();
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
        // Validate every candidate file before pausing or mutating live services.
        // The exact fingerprint returned by preflight must remain unchanged while
        // the already-validated files are applied.
        ReloadFingerprint validated = preflightReload();

        miningProfiler.stopSession();
        progression.pause();
        if (progressListener != null) {
            progressListener.invalidateAllActiveContexts();
        }
        try {
            if (!validated.equals(configurationFingerprint())) {
                throw new IllegalStateException(
                        "Configuration files changed after reload validation; retry /pt reload.");
            }
            reloadConfig();
            settings.load(getConfig());
            messages.reload();
            categories.reload();
            tools.reload();
            itemService.clearDefinitionCaches();
            progression.clearDefinitionCaches();
            worldMenus.reload();
            ReloadFingerprint applied = configurationFingerprint();
            if (!validated.equals(applied)) {
                throw new IllegalStateException(
                        "Configuration files changed while /pt reload was being applied; retry reload.");
            }
            naturalBlocks.start();
            abilities.start();
            getServer().getOnlinePlayers().forEach(activations::reconcile);
            scheduleRegistrySave();
            lastReloadEpochMillis = System.currentTimeMillis();
            lastReloadState = "SUCCESS";
            coreBridge.markReady("PlexonTools reloaded; API and progression services ready");
        } catch (Exception exception) {
            lastReloadEpochMillis = System.currentTimeMillis();
            lastReloadState = "FAILED/DEGRADED: " + exception.getClass().getSimpleName();
            coreBridge.markDegraded("PlexonTools reload failed: "
                    + exception.getClass().getSimpleName());
            throw exception;
        } finally {
            progression.start();
        }
    }

    /**
     * Parses the complete reload candidate using detached service instances.
     * No live listener, scheduler, cache, settings object or repository is
     * mutated until every file has passed this gate. Returns the exact validated
     * file fingerprint so no post-validation edit can escape the transaction.
     */
    private ReloadFingerprint preflightReload() throws Exception {
        ReloadFingerprint before = configurationFingerprint();

        File configFile = new File(getDataFolder(), "config.yml");
        YamlConfiguration candidateConfig = new YamlConfiguration();
        candidateConfig.load(configFile);
        PluginSettings candidateSettings = new PluginSettings();
        candidateSettings.load(candidateConfig);

        MessageService candidateMessages = new MessageService(this);
        candidateMessages.reload();

        CategoryRepository candidateCategories = new CategoryRepository(this);
        candidateCategories.reload();
        requireCompiledCount("categories.yml", "categories", candidateCategories.size());

        ToolConfigRepository candidateTools = new ToolConfigRepository(
                this, candidateSettings, candidateCategories);
        candidateTools.reload();
        requireCompiledCount("tools.yml", "tools", candidateTools.size());

        WorldMenuRepository candidateWorldMenus = new WorldMenuRepository(this);
        candidateWorldMenus.reload();
        requireCompiledCount("menus.yml", "worlds", candidateWorldMenus.size());

        ReloadFingerprint after = configurationFingerprint();
        if (!before.equals(after)) {
            throw new IllegalStateException(
                    "Configuration files changed during reload validation; retry /pt reload.");
        }
        return after;
    }

    private void requireCompiledCount(String fileName, String root, int compiledCount)
            throws IOException, org.bukkit.configuration.InvalidConfigurationException {
        YamlConfiguration raw = new YamlConfiguration();
        raw.load(new File(getDataFolder(), fileName));
        ConfigurationSection section = raw.getConfigurationSection(root);
        int configuredCount = section == null ? 0 : section.getKeys(false).size();
        if (configuredCount != compiledCount) {
            throw new IllegalArgumentException(fileName + " contains " + configuredCount
                    + " configured " + root + " entries but only " + compiledCount
                    + " compiled successfully; reload refused.");
        }
    }

    private ReloadFingerprint configurationFingerprint() throws IOException {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String name : CONFIGURATION_RESOURCES) {
            File file = new File(getDataFolder(), name);
            hashes.put(name, sha256(Files.readAllBytes(file.toPath())));
        }
        return new ReloadFingerprint(Map.copyOf(hashes));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
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
        NaturalBlockTracker.Diagnostics provenance = naturalBlocks.diagnostics();
        InstanceRegistry.PersistenceDiagnostics persistence = instanceRegistry.persistenceDiagnostics();
        var bulk = abilities.bulkBreakDiagnostics();
        return List.of(
                "<gradient:#66BB6A:#42A5F5><bold>PlexonTools Diagnostics</bold></gradient>",
                diagnostic("Plugin", getPluginMeta().getVersion()),
                diagnostic("Paper", Bukkit.getVersion()),
                diagnostic("Java", System.getProperty("java.version", "unknown")),
                diagnostic("Authority", "LOCAL gameplay • LOCAL provenance • Core lifecycle only"),
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
                        + persistence.pendingWrites() + " pending writes"),
                diagnostic("Persistence", persistence.committedBatches() + " commits • avg batch "
                        + String.format(java.util.Locale.ROOT, "%.1f", persistence.averageBatchSize())
                        + " • " + persistence.dirtyMutations() + " dirty mutations"),
                diagnostic("Storage pressure", "high-water " + persistence.queueHighWaterMark()
                        + " • " + persistence.pressureFlushes() + " pressure flushes • "
                        + persistence.failedWriteBatches() + " failed batches"),
                diagnostic("Natural blocks", provenance.active() ? "ENABLED" : "DISABLED"),
                diagnostic("Natural provenance", provenance.loadedChunks() + " chunks • "
                        + provenance.trackedPlacedPositions() + " placed • "
                        + provenance.unknownChunks() + " unknown • "
                        + provenance.pendingLoads() + " pending loads"),
                diagnostic("Origin decisions", "natural=" + provenance.naturalDecisions()
                        + " • placed=" + provenance.playerPlacedDecisions()
                        + " • unknown=" + provenance.unknownDecisions()
                        + " • disabled=" + provenance.disabledDecisions()
                        + " • rejected=" + provenance.rejectedDecisions()),
                diagnostic("Provenance I/O", provenance.loadBatches() + " batches • "
                        + provenance.retries() + " retries • "
                        + provenance.failedLoads() + " failed"),
                diagnostic("Ability runtime", abilities.activePassiveHolderCount()
                        + " passive holders • " + abilities.pendingBlockDropContextCount()
                        + " pending drop contexts"),
                diagnostic("Area Mine", "requested=" + bulk.requestedMode() + " • effective="
                        + bulk.effectiveMode() + " • max " + bulk.maxSecondaryBlocks()
                        + "/activation • " + bulk.maxBlocksPerPlayerPerTick() + "/player/tick"),
                diagnostic("Area Mine totals", bulk.acceptedBlocks() + " accepted / "
                        + bulk.dispatchedBlocks() + " dispatched • "
                        + bulk.budgetLimitedActivations() + " safety-blocked/limited activations"),
                diagnostic("Reload", lastReloadState + " • "
                        + Instant.ofEpochMilli(lastReloadEpochMillis)),
                diagnostic("Mining profiler", miningProfiler.enabled()
                        ? "RUNNING • " + miningProfiler.blockSamples() + " samples" : "STOPPED"),
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

    private record ReloadFingerprint(Map<String, String> sha256ByFile) {}
}
