package com.plexon.tools.api;

import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.storage.InstanceRegistry;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Internal implementation registered through Bukkit's ServicesManager. */
public final class DefaultPlexonToolsAPI implements PlexonToolsAPI {
    private final ToolConfigRepository definitions;
    private final ToolItemService items;
    private final InstanceRegistry instances;

    public DefaultPlexonToolsAPI(
            ToolConfigRepository definitions,
            ToolItemService items,
            InstanceRegistry instances
    ) {
        this.definitions = definitions;
        this.items = items;
        this.instances = instances;
    }

    @Override
    public Optional<ToolView> tool(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        ToolItemService.ToolIdentityInspection inspection = items.inspectIdentity(stack);
        if (!inspection.tagged() || inspection.identity() == null) {
            return Optional.empty();
        }
        UUID instanceId = inspection.identity().instanceId();
        InstanceRegistry.InstanceRecord record = instances.findCached(instanceId);
        if (record != null) {
            if (!record.toolId().equalsIgnoreCase(inspection.identity().toolId())
                    || !record.ownerId().equals(inspection.identity().ownerId())) {
                return Optional.empty();
            }
            return snapshot(record.state(), stack.getType());
        }
        return items.read(stack).flatMap(state -> snapshot(state, stack.getType()));
    }

    @Override
    public Optional<ToolView> tool(UUID instanceId) {
        if (instanceId == null) {
            return Optional.empty();
        }
        InstanceRegistry.InstanceRecord record = instances.findCached(instanceId);
        if (record == null) {
            return Optional.empty();
        }
        return snapshot(record.state(), materialFor(record.state()));
    }

    @Override
    public Optional<ToolDefinitionView> definition(String toolId) {
        return definitions.find(toolId).map(DefaultPlexonToolsAPI::definitionSnapshot);
    }

    @Override
    public Collection<ToolDefinitionView> definitions() {
        return definitions.all().stream()
                .map(DefaultPlexonToolsAPI::definitionSnapshot)
                .toList();
    }

    @Override
    public boolean isPlexonTool(ItemStack stack) {
        return tool(stack).isPresent();
    }

    private Optional<ToolView> snapshot(ToolState state, Material material) {
        ToolDefinition definition = definitions.findCached(state.toolId());
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.of(new ToolView(
                state.instanceId(),
                state.toolId(),
                state.categoryId().isBlank() ? definition.category() : state.categoryId(),
                state.ownerId(),
                state.boundWorld(),
                state.level(),
                state.progress(),
                state.targetProgress(),
                progressType(definition),
                material));
    }

    private Material materialFor(ToolState state) {
        ToolDefinition definition = definitions.findCached(state.toolId());
        return definition == null
                ? Material.AIR
                : definition.level(state.level()).map(level -> level.material())
                        .orElse(definition.baseMaterial());
    }

    private static ToolDefinitionView definitionSnapshot(ToolDefinition definition) {
        return new ToolDefinitionView(
                definition.id(),
                definition.enabled(),
                definition.displayName(),
                definition.baseMaterial(),
                definition.allowedWorlds(),
                definition.category(),
                definition.progressionScope().name().toLowerCase(Locale.ROOT),
                definition.progressionAnchorWorld(),
                progressType(definition),
                definition.maxLevel());
    }

    private static String progressType(ToolDefinition definition) {
        return definition.trackingType().name().toLowerCase(Locale.ROOT);
    }
}
