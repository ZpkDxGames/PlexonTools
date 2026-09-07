package com.plexon.tools.integration.core;

/** Optional PlexonCore integration boundary. No Core runtime types leak through this interface. */
public interface CoreBridge {
    String SUPPORTED_API_RANGE = ">=1.0 <2.0";
    String MODULE_ID = "tools";

    boolean installed();
    boolean available();
    boolean compatible();
    String pluginVersion();
    String apiVersion();
    String mode();
    String registrationState();
    String detail();
    void registerStarting();
    void markReady(String detail);
    void markDegraded(String detail);
    void markFailed(String detail);
    void unregister();
}
