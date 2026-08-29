package com.plexon.tools.model;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.List;
import java.util.Map;

public record ToolLevel(
        int number,
        LevelRequirement requirement,
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
        Integer customModelData,
        Map<ToolAbilityType, ToolAbilitySettings> abilities
) {
    public ToolLevel {
        if (number < 1) {
            throw new IllegalArgumentException("Level number must be positive.");
        }
        if (requirement == null) {
            throw new IllegalArgumentException("Level requirement is required.");
        }
        enchantments = Map.copyOf(enchantments);
        lore = List.copyOf(lore);
        abilities = Map.copyOf(abilities);
    }

    public ToolLevel withNumber(int newNumber) {
        return new ToolLevel(newNumber, requirement, displayName, displayNameOverride,
                enchantments, material, materialOverride, lore, unbreakable, glint,
                hideEnchantments, hideAttributes, customModelData, abilities);
    }

    public ToolLevel withRequirement(LevelRequirement newRequirement) {
        return new ToolLevel(number, newRequirement, displayName, displayNameOverride,
                enchantments, material, materialOverride, lore, unbreakable, glint,
                hideEnchantments, hideAttributes, customModelData, abilities);
    }

    public ToolLevel withAbilities(Map<ToolAbilityType, ToolAbilitySettings> newAbilities) {
        return new ToolLevel(number, requirement, displayName, displayNameOverride,
                enchantments, material, materialOverride, lore, unbreakable, glint,
                hideEnchantments, hideAttributes, customModelData, newAbilities);
    }
}
