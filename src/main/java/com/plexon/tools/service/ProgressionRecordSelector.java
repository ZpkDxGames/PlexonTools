package com.plexon.tools.service;

import com.plexon.tools.model.LevelRequirement;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.storage.InstanceRegistry;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Selects the one authoritative record used by a cross-world tool. */
public final class ProgressionRecordSelector {
    private ProgressionRecordSelector() {
    }

    public static Optional<InstanceRegistry.InstanceRecord> canonical(
            ToolDefinition definition,
            Collection<InstanceRegistry.InstanceRecord> records
    ) {
        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }
        List<InstanceRegistry.InstanceRecord> candidates = records.stream()
                .filter(record -> record.toolId().equalsIgnoreCase(definition.id()))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (definition.sharesProgressAcrossWorlds()) {
            List<InstanceRegistry.InstanceRecord> anchored = candidates.stream()
                    .filter(record -> record.boundWorld().equalsIgnoreCase(
                            definition.progressionAnchorWorld()))
                    .toList();
            if (!anchored.isEmpty()) {
                candidates = anchored;
            }
        }
        return candidates.stream().max(recordComparator(definition));
    }

    private static Comparator<InstanceRegistry.InstanceRecord> recordComparator(
            ToolDefinition definition
    ) {
        return Comparator.comparingInt(InstanceRegistry.InstanceRecord::level)
                .thenComparingLong(record -> creditedProgress(definition, record))
                .thenComparingLong(InstanceRegistry.InstanceRecord::lifetime)
                .thenComparingLong(InstanceRegistry.InstanceRecord::updatedAt)
                .thenComparing(record -> record.instanceId().toString());
    }

    private static long creditedProgress(
            ToolDefinition definition,
            InstanceRegistry.InstanceRecord record
    ) {
        LevelRequirement requirement = definition.level(record.level())
                .map(level -> level.requirement()).orElse(null);
        return requirement == null
                ? record.progress()
                : requirement.creditedProgress(record.progress(), record.targetProgress());
    }
}
