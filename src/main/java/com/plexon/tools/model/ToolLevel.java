package com.plexon.tools.model;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.List;
import java.util.Map;

public record ToolLevel(
        int number,
        long requirement,
        String displayName,
        boolean displayNameOverride,
        Map<Enchantment, Integer> enchantments,
        Material material,
        boolean materialOverride,
        List<String> lore,
        boolean unbreakable,
        GlintMode glint,
        boolean hideEnchantments,
        boolean hideAttributes,
        Integer customModelData
) {
    public ToolLevel {
        enchantments = Map.copyOf(enchantments);
        lore = List.copyOf(lore);
    }

    public ToolLevel withNumber(int newNumber) {
        return new ToolLevel(newNumber, requirement, displayName, displayNameOverride,
                enchantments, material, materialOverride, lore, unbreakable, glint,
                hideEnchantments, hideAttributes, customModelData);
    }

    public ToolLevel withRequirement(long newRequirement) {
        return new ToolLevel(number, newRequirement, displayName, displayNameOverride,
                enchantments, material, materialOverride, lore, unbreakable, glint,
                hideEnchantments, hideAttributes, customModelData);
    }
}
