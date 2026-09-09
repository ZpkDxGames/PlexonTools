package com.plexon.tools.integration.core;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleDescriptor;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.Set;

/** Core-backed bridge, loaded reflectively only when PlexonCore is enabled. */
public final class PlexonCoreBridge implements CoreBridge {
    private static final Set<String> CAPABILITIES = Set.of(
            "tool-engine",
            "progressive-tools",
            "tool-api",
            "tool-progress-event",
            "tool-level-up-event",
            "sqlite-persistence",
            "custom-item-metadata",
            "world-bound-tools",
            "tool-categories",
            "tool-abilities",
            "core2-runtime-candidate");

    private final JavaPlugin plugin;
    private final PlexonCoreAPI core;
    private final CoreVersion version;
    private final boolean runtimeAvailable;
    private final boolean compatible;
    private final String registrationApiRange;
    private boolean ownsRegistration;
    private String registrationState = "NOT_REGISTERED";
    private String detail;

    public PlexonCoreBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<PlexonCoreAPI> registration =
                Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
        if (registration == null) {
            throw new IllegalStateException("PlexonCore API service is not registered");
        }
        this.core = registration.getProvider();
        this.version = core.version();

        boolean api2 = core.supportsApi(2, 0);
        boolean api1 = core.supportsApi(1, 0);
        this.runtimeAvailable = api2;
        this.compatible = api2 || api1;
        this.registrationApiRange = api2 ? RUNTIME_API_RANGE : LEGACY_API_RANGE;
        this.detail = api2
                ? "PlexonCore 2 runtime is available; local mining remains authoritative until 4.3 parity gates pass"
                : api1
                        ? "PlexonCore legacy API resolved"
                        : "Core API " + version.apiVersion()
                                + " is outside supported ranges " + SUPPORTED_API_RANGE;
    }

    @Override public boolean installed() { return true; }
    @Override public boolean available() { return compatible; }
    @Override public boolean compatible() { return compatible; }
    @Override public boolean runtimeAvailable() { return runtimeAvailable; }
    @Override public String pluginVersion() { return version.pluginVersion(); }
    @Override public String apiVersion() { return version.apiVersion(); }
    @Override public String registrationApiRange() { return registrationApiRange; }

    /**
     * Runtime acquisition is intentionally not reported as CORE_RUNTIME until
     * the final-phase/cancellation, legacy provenance and performance parity
     * gates have been demonstrated against the released Core 2 implementation.
     */
    @Override
    public String mode() {
        return compatible && ownsRegistration ? "CORE_LEGACY" : "STANDALONE";
    }

    @Override
    public String registrationState() {
        if (ownsRegistration) {
            return core.modules().find(MODULE_ID)
                    .map(descriptor -> descriptor.state().name())
                    .orElse("NOT_REGISTERED");
        }
        return registrationState;
    }

    @Override
    public String detail() {
        if (ownsRegistration) {
            return core.modules().find(MODULE_ID).map(ModuleDescriptor::detail).orElse(detail);
        }
        return detail;
    }

    @Override
    public void registerStarting() {
        if (!compatible) {
            return;
        }
        ModuleDescriptor descriptor = new ModuleDescriptor(
                MODULE_ID,
                "PlexonTools",
                plugin.getName(),
                plugin.getPluginMeta().getVersion(),
                plugin,
                ModuleVersionRange.parse(registrationApiRange),
                CAPABILITIES,
                ModuleState.STARTING,
                runtimeAvailable
                        ? "Initializing PlexonTools with Core 2 compatibility; local mining authority retained"
                        : "Initializing PlexonTools",
                Instant.now());

        ModuleRegistry.RegistrationResult result = core.modules().register(descriptor);
        ModuleDescriptor registered = result.descriptor();
        ownsRegistration = registered != null && registered.plugin() == plugin;

        if (!result.success() && !ownsRegistration && registered != null
                && !registered.plugin().isEnabled()) {
            core.modules().unregister(MODULE_ID);
            result = core.modules().register(descriptor);
            registered = result.descriptor();
            ownsRegistration = registered != null && registered.plugin() == plugin;
        }

        registrationState = registered == null ? "NOT_REGISTERED" : registered.state().name();
        detail = result.message();
        if (!result.success() && !ownsRegistration) {
            plugin.getLogger().warning(
                    "PlexonCore module registration rejected: " + result.message());
        }
    }

    @Override public void markReady(String detail) { update(ModuleState.READY, detail); }
    @Override public void markDegraded(String detail) { update(ModuleState.DEGRADED, detail); }
    @Override public void markFailed(String detail) { update(ModuleState.FAILED, detail); }

    private void update(ModuleState state, String newDetail) {
        if (!compatible || !ownsRegistration) {
            return;
        }
        core.modules().updateState(MODULE_ID, state, newDetail);
        registrationState = state.name();
        detail = newDetail == null ? "" : newDetail;
    }

    @Override
    public void unregister() {
        if (!ownsRegistration) {
            return;
        }
        core.modules().find(MODULE_ID)
                .filter(descriptor -> descriptor.plugin() == plugin)
                .ifPresent(descriptor -> {
                    core.modules().updateState(MODULE_ID, ModuleState.DISABLED,
                            "PlexonTools disabled cleanly");
                    core.modules().unregister(MODULE_ID);
                });
        ownsRegistration = false;
        registrationState = "UNREGISTERED";
    }
}
