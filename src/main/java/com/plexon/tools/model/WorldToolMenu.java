package com.plexon.tools.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

public record WorldToolMenu(
        String worldName,
        String title,
        int rows,
        Material fillerMaterial,
        String fillerName,
        Map<String, Integer> toolSlots
) {
    public WorldToolMenu {
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("World name is required.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("World menu title is required.");
        }
        if (rows < 3 || rows > 6) {
            throw new IllegalArgumentException("World menu rows must be between 3 and 6.");
        }
        if (fillerMaterial == null || !fillerMaterial.isItem() || fillerMaterial.isAir()) {
            throw new IllegalArgumentException("World menu filler must be an item material.");
        }
        fillerName = fillerName == null ? " " : fillerName;

        Map<String, Integer> normalized = new LinkedHashMap<>();
        toolSlots.forEach((toolId, slot) -> {
            String id = normalize(toolId);
            if (id.isBlank() || slot == null || !isContentSlot(rows, slot)) {
                throw new IllegalArgumentException("Invalid world menu tool slot for " + toolId + ".");
            }
            if (normalized.containsValue(slot)) {
                throw new IllegalArgumentException("Duplicate world menu slot " + slot + ".");
            }
            normalized.put(id, slot);
        });
        toolSlots = Collections.unmodifiableMap(normalized);
    }

    public int size() {
        return rows * 9;
    }

    public OptionalInt slot(String toolId) {
        Integer slot = toolSlots.get(normalize(toolId));
        return slot == null ? OptionalInt.empty() : OptionalInt.of(slot);
    }

    public boolean contains(String toolId) {
        return toolSlots.containsKey(normalize(toolId));
    }

    public static boolean isContentSlot(int rows, int slot) {
        if (rows < 3 || rows > 6 || slot < 0 || slot >= rows * 9) {
            return false;
        }
        int row = slot / 9;
        int column = slot % 9;
        return row > 0 && row < rows - 1 && column > 0 && column < 8;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
