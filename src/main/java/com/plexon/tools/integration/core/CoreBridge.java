package com.plexon.tools.integration.core;

/** Optional PlexonCore integration boundary. No Core runtime types leak through this interface. */
public interface CoreBridge {
    String LEGACY_API_RANGE = ">=1.0 <2.0";
    String RUNTIME_API_RANGE = ">=2.0 <3.0";
    String SUPPORTED_API_RANGE = LEGACY_API_RANGE + " or " + RUNTIME_API_RANGE;
    String MODULE_ID = "tools";

    boolean installed();
    boolean available();
    boolean compatible();
    boolean runtimeAvailable();
    String pluginVersion();
    String apiVersion();
    String registrationApiRange();
    String mode();
    String registrationState();
    String detail();
    void registerStarting();
    void markReady(String detail);
    void markDegraded(String detail);
    void markFailed(String detail);
    void unregister();
}
