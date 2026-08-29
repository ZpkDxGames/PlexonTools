package com.plexon.tools.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolAbilitySettingsTest {
    @Test
    void expBoosterDefaultsToOnePointFive() {
        ToolAbilitySettings settings = ToolAbilitySettings.defaults(ToolAbilityType.EXP_BOOSTER);

        assertEquals(1.5D, settings.multiplier());
        assertEquals(AbilityTarget.HOLDER, settings.potionTarget());
    }

    @Test
    void potionSettingsUseOneBasedAmplifiers() {
        ToolAbilitySettings settings = ToolAbilitySettings.defaults(ToolAbilityType.MOB_POTION_EFFECT)
                .withPotion("minecraft:speed", 3, 200, AbilityTarget.TARGET);

        assertEquals("minecraft:speed", settings.potionEffect());
        assertEquals(3, settings.potionLevel());
        assertEquals(200, settings.durationTicks());
        assertEquals(AbilityTarget.TARGET, settings.potionTarget());
    }

    @Test
    void rejectsUnsafeConfigurationBounds() {
        ToolAbilitySettings defaults = ToolAbilitySettings.defaults(ToolAbilityType.EXP_BOOSTER);

        assertThrows(IllegalArgumentException.class, () -> defaults.withMultiplier(0.5D));
        assertThrows(IllegalArgumentException.class, () -> defaults.withMultiplier(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> defaults.withPotion(
                "minecraft:haste", 257, 100, AbilityTarget.HOLDER));
    }
}
