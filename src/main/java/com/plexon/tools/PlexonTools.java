package com.plexon.tools;

import com.plexon.tools.command.PlexonToolsCommand;
import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.config.CategoryRepository;
import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.gui.GuiManager;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.listener.ToolProgressListener;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.service.ChatPromptService;
import com.plexon.tools.service.AbilityService;
import com.plexon.tools.service.ProgressionService;
import com.plexon.tools.service.ToolGrantService;
import com.plexon.tools.storage.InstanceRegistry;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;

public final class PlexonTools extends JavaPlugin {
    private final PluginSettings settings = new PluginSettings();
    private MessageService messages;
    private CategoryRepository categories;
    private ToolConfigRepository tools;
    private InstanceRegistry instanceRegistry;
    private ChatPromptService prompts;
    private AbilityService abilities;
    private BukkitTask registrySaveTask;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            saveBundledResource("tools.yml");
            saveBundledResource("messages.yml");
            saveBundledResource("categories.yml");

            settings.load(getConfig());
            messages = new MessageService(this);
            messages.reload();
            categories = new CategoryRepository(this);
            categories.reload();
            tools = new ToolConfigRepository(this, settings, categories);
            tools.reload();
            instanceRegistry = new InstanceRegistry(this);
            instanceRegistry.load();

            ToolItemService itemService = new ToolItemService(this, messages, settings, categories);
            ProgressionService progression = new ProgressionService(
                    itemService, instanceRegistry, settings, messages);
            abilities = new AbilityService(this, tools, itemService, progression);
            ToolGrantService grants = new ToolGrantService(itemService, instanceRegistry, messages);
            prompts = new ChatPromptService(this, messages);
            GuiManager gui = new GuiManager(this, categories, tools, itemService,
                    grants, prompts, settings, messages);

            getServer().getPluginManager().registerEvents(
                    new ToolProgressListener(tools, itemService, progression, abilities, settings), this);
            getServer().getPluginManager().registerEvents(abilities, this);
            getServer().getPluginManager().registerEvents(prompts, this);
            getServer().getPluginManager().registerEvents(gui, this);

            PluginCommand command = Objects.requireNonNull(getCommand("plexontools"),
                    "plexontools command is missing from plugin.yml");
            PlexonToolsCommand executor = new PlexonToolsCommand(
                    categories, tools, grants, gui, messages, this::reloadPlugin);
            command.setExecutor(executor);
            command.setTabCompleter(executor);

            scheduleRegistrySave();
            abilities.start();
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().info("PlexonTools " + getPluginMeta().getVersion() + " enabled");
            getLogger().info("Loaded tools: " + tools.size());
            getLogger().info("Loaded categories: " + categories.size());
            getLogger().info("Tracked instances: " + instanceRegistry.size());
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
        if (registrySaveTask != null) {
            registrySaveTask.cancel();
        }
        if (instanceRegistry != null) {
            try {
                instanceRegistry.flushBlocking();
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Could not flush the tool instance registry", exception);
            }
        }
    }

    private void reloadPlugin() throws Exception {
        reloadConfig();
        settings.load(getConfig());
        messages.reload();
        categories.reload();
        tools.reload();
        scheduleRegistrySave();
    }

    private void scheduleRegistrySave() {
        if (registrySaveTask != null) {
            registrySaveTask.cancel();
        }
        registrySaveTask = getServer().getScheduler().runTaskTimerAsynchronously(
                this,
                instanceRegistry::flushAsync,
                settings.registryAutosaveTicks(),
                settings.registryAutosaveTicks()
        );
    }

    private void saveBundledResource(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }
}
