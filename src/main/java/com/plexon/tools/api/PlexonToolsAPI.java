package com.plexon.tools.api;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Stable read-only public API for PlexonTools integrations.
 *
 * <p>The returned records are immutable snapshots. Consumers must not assume
 * that a snapshot remains current after a later gameplay mutation.</p>
 */
public interface PlexonToolsAPI {
    Optional<ToolView> tool(ItemStack stack);

    Optional<ToolView> tool(UUID instanceId);

    Optional<ToolDefinitionView> definition(String toolId);

    Collection<ToolDefinitionView> definitions();

    boolean isPlexonTool(ItemStack stack);
}
