package com.plexon.tools.model;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.List;
import java.util.Map;

public record ToolLevel(
        int number,
        long requirement,
        Map<Enchantment, Integer> enchantments,
        Material materialUpgrade,
        List<String> lore
) {
    public ToolLevel {
        enchantments = Map.copyOf(enchantments);
        lore = List.copyOf(lore);
    }
}
