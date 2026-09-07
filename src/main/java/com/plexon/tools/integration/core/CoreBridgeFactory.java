package com.plexon.tools.integration.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;

/** Resolves Core without linking Core API classes when the optional plugin is absent. */
public final class CoreBridgeFactory {
    private static final String CORE_PLUGIN = "PlexonCore";
    private static final String CORE_BRIDGE_CLASS =
            "com.plexon.tools.integration.core.PlexonCoreBridge";

    private CoreBridgeFactory() {}

    public static CoreBridge resolve(JavaPlugin plugin) {
        Plugin corePlugin = Bukkit.getPluginManager().getPlugin(CORE_PLUGIN);
        if (corePlugin == null) {
            plugin.getLogger().info(
                    "PlexonCore not installed; starting PlexonTools in standalone mode.");
            return new StandaloneCoreBridge(false, "-", "-", "PlexonCore is not installed");
        }

        String version = corePlugin.getPluginMeta().getVersion();
        if (!corePlugin.isEnabled()) {
            plugin.getLogger().warning(
                    "PlexonCore is installed but disabled; starting PlexonTools in standalone mode.");
            return new StandaloneCoreBridge(
                    true, version, "-", "PlexonCore is installed but disabled");
        }

        try {
            Class<?> type = Class.forName(
                    CORE_BRIDGE_CLASS, true, CoreBridgeFactory.class.getClassLoader());
            Object bridge = type.getConstructor(JavaPlugin.class).newInstance(plugin);
            CoreBridge resolved = (CoreBridge) bridge;
            if (!resolved.compatible()) {
                plugin.getLogger().warning("PlexonCore API " + resolved.apiVersion()
                        + " is outside supported range " + CoreBridge.SUPPORTED_API_RANGE
                        + "; starting PlexonTools in standalone mode.");
                return new StandaloneCoreBridge(true, resolved.pluginVersion(), resolved.apiVersion(),
                        resolved.detail());
            }
            return resolved;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            plugin.getLogger().log(Level.WARNING,
                    "PlexonCore API could not be resolved; using standalone mode.", cause);
            return new StandaloneCoreBridge(
                    true, version, "-", "PlexonCore API service is unavailable");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "PlexonCore could not be linked safely; using standalone mode.", exception);
            return new StandaloneCoreBridge(
                    true, version, "-", "PlexonCore API linkage is unavailable");
        }
    }
}
