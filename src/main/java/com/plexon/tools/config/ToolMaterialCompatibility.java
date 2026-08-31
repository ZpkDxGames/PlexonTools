package com.plexon.tools.config;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.Locale;
import java.util.Optional;

/**
 * Guards block objectives that the configured vanilla tool profile cannot
 * actually mine. Unknown/custom item families remain unrestricted so custom
 * model items and third-party behaviour stay backwards compatible.
 */
final class ToolMaterialCompatibility {
    private ToolMaterialCompatibility() {
    }

    static boolean supports(Material tool, Material block) {
        return incompatibilityReason(tool, block).isEmpty();
    }

    static boolean supports(Material tool, String requiredFamily, int requiredTier) {
        ToolFamily family = ToolFamily.from(tool);
        return family == null || (family.label.equalsIgnoreCase(requiredFamily)
                && toolTier(tool) >= requiredTier);
    }

    static Optional<String> incompatibilityReason(Material tool, Material block) {
        ToolFamily family = ToolFamily.from(tool);
        if (family == null) {
            return Optional.empty();
        }
        if (!isMineable(family, block)) {
            return Optional.of(tool.name() + " cannot mine " + block.name()
                    + "; the block is not tagged as " + family.label + "-mineable");
        }

        int requiredTier = requiredTier(block);
        int actualTier = toolTier(tool);
        if (actualTier < requiredTier) {
            return Optional.of(tool.name() + " cannot harvest " + block.name()
                    + "; it requires a " + tierName(requiredTier) + "-tier tool");
        }
        return Optional.empty();
    }

    private static int requiredTier(Material block) {
        if (Tag.NEEDS_DIAMOND_TOOL.isTagged(block)) {
            return 3;
        }
        if (Tag.NEEDS_IRON_TOOL.isTagged(block)) {
            return 2;
        }
        if (Tag.NEEDS_STONE_TOOL.isTagged(block)) {
            return 1;
        }
        return 0;
    }

    private static boolean isMineable(ToolFamily family, Material block) {
        return switch (family) {
            case PICKAXE -> Tag.MINEABLE_PICKAXE.isTagged(block);
            case AXE -> Tag.MINEABLE_AXE.isTagged(block);
            case SHOVEL -> Tag.MINEABLE_SHOVEL.isTagged(block);
            case HOE -> Tag.MINEABLE_HOE.isTagged(block);
        };
    }

    private static int toolTier(Material tool) {
        String name = tool.name();
        if (name.startsWith("NETHERITE_") || name.startsWith("DIAMOND_")) {
            return 3;
        }
        if (name.startsWith("IRON_")) {
            return 2;
        }
        if (name.startsWith("STONE_")) {
            return 1;
        }
        return 0;
    }

    private static String tierName(int tier) {
        return switch (tier) {
            case 1 -> "stone";
            case 2 -> "iron";
            case 3 -> "diamond";
            default -> "wood";
        };
    }

    private enum ToolFamily {
        PICKAXE("pickaxe"),
        AXE("axe"),
        SHOVEL("shovel"),
        HOE("hoe");

        private final String label;

        ToolFamily(String label) {
            this.label = label;
        }

        static ToolFamily from(Material material) {
            String name = material.name().toUpperCase(Locale.ROOT);
            for (ToolFamily family : values()) {
                if (name.endsWith("_" + family.name())) {
                    return family;
                }
            }
            return null;
        }
    }
}
