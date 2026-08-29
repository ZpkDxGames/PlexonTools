package com.plexon.tools.model;

import java.util.Objects;

public record ToolAbilitySettings(
        ToolAbilityType type,
        double multiplier,
        String potionEffect,
        int potionLevel,
        int durationTicks,
        AbilityTarget potionTarget
) {
    public ToolAbilitySettings {
        Objects.requireNonNull(type, "Ability type is required.");
        if (!Double.isFinite(multiplier) || multiplier < 1.0D || multiplier > 100.0D) {
            throw new IllegalArgumentException("Ability multiplier must be between 1.0 and 100.0.");
        }
        potionEffect = potionEffect == null || potionEffect.isBlank()
                ? "minecraft:haste"
                : potionEffect.trim().toLowerCase(java.util.Locale.ROOT);
        if (potionLevel < 1 || potionLevel > 256) {
            throw new IllegalArgumentException("Potion level must be between 1 and 256.");
        }
        if (durationTicks < 1 || durationTicks > 20 * 60 * 60) {
            throw new IllegalArgumentException("Potion duration must be between 1 tick and 1 hour.");
        }
        Objects.requireNonNull(potionTarget, "Potion target is required.");
    }

    public static ToolAbilitySettings defaults(ToolAbilityType type) {
        return new ToolAbilitySettings(type,
                type == ToolAbilityType.EXP_BOOSTER ? 1.5D : 1.0D,
                "minecraft:haste", 2, 100, AbilityTarget.HOLDER);
    }

    public ToolAbilitySettings withMultiplier(double value) {
        return new ToolAbilitySettings(type, value, potionEffect, potionLevel, durationTicks, potionTarget);
    }

    public ToolAbilitySettings withPotion(
            String effect,
            int level,
            int duration,
            AbilityTarget target
    ) {
        return new ToolAbilitySettings(type, multiplier, effect, level, duration, target);
    }
}
