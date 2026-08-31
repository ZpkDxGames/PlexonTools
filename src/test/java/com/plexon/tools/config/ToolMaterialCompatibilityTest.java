package com.plexon.tools.config;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ToolMaterialCompatibilityTest {
    @Test
    void enforcesVanillaToolFamilies() {
        assertTrue(ToolMaterialCompatibility.supports(
                Material.WOODEN_PICKAXE, "pickaxe", 0));
        assertFalse(ToolMaterialCompatibility.supports(
                Material.WOODEN_PICKAXE, "axe", 0));
        assertTrue(ToolMaterialCompatibility.supports(
                Material.WOODEN_AXE, "axe", 0));
        assertFalse(ToolMaterialCompatibility.supports(
                Material.WOODEN_AXE, "pickaxe", 0));
        assertTrue(ToolMaterialCompatibility.supports(
                Material.WOODEN_SHOVEL, "shovel", 0));
        assertFalse(ToolMaterialCompatibility.supports(
                Material.WOODEN_SHOVEL, "axe", 0));
    }

    @Test
    void enforcesHarvestTiersDuringMaterialProgression() {
        assertFalse(ToolMaterialCompatibility.supports(
                Material.WOODEN_PICKAXE, "pickaxe", 1));
        assertTrue(ToolMaterialCompatibility.supports(
                Material.STONE_PICKAXE, "pickaxe", 1));
        assertFalse(ToolMaterialCompatibility.supports(
                Material.STONE_PICKAXE, "pickaxe", 2));
        assertTrue(ToolMaterialCompatibility.supports(
                Material.IRON_PICKAXE, "pickaxe", 2));
        assertFalse(ToolMaterialCompatibility.supports(
                Material.IRON_PICKAXE, "pickaxe", 3));
        assertTrue(ToolMaterialCompatibility.supports(
                Material.DIAMOND_PICKAXE, "pickaxe", 3));
    }

    @Test
    void leavesUnknownCustomFamiliesBackwardsCompatible() {
        assertTrue(ToolMaterialCompatibility.supports(
                Material.BLAZE_ROD, "pickaxe", 3));
        assertTrue(ToolMaterialCompatibility.supports(
                Material.WOODEN_SWORD, "shovel", 3));
    }
}
