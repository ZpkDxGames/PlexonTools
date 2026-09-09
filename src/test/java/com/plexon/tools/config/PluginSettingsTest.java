package com.plexon.tools.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PluginSettingsTest {
    @Test
    void preservesAdministratorLoreOrderAndRequirementFormats() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tool-lore.enabled", true);
        config.set("tool-lore.template", List.of(
                "<gold>{owner_name}</gold>",
                "{requirement_lines}",
                "<gray>{progress_bar}</gray>"));
        config.set("tool-lore.requirements.general-line", "GENERAL {requirement_current}");
        config.set("tool-lore.requirements.specific-line", "SPECIFIC {requirement_target}");
        config.set("tool-lore.requirements.maximum-line", "MAXIMUM");
        config.set("tool-lore.enchantments.line", "ENCHANT {enchantment_name}");
        config.set("tool-lore.enchantments.empty-line", "NO ENCHANTMENTS");

        PluginSettings.LoreSettings settings = PluginSettings.loreSettings(config);

        assertEquals(List.of(
                "<gold>{owner_name}</gold>",
                "{requirement_lines}",
                "<gray>{progress_bar}</gray>"), settings.template());
        assertEquals("GENERAL {requirement_current}", settings.generalRequirementLine());
        assertEquals("SPECIFIC {requirement_target}", settings.specificRequirementLine());
        assertEquals("MAXIMUM", settings.maximumRequirementLine());
        assertEquals("ENCHANT {enchantment_name}", settings.enchantmentLine());
        assertEquals("NO ENCHANTMENTS", settings.emptyEnchantmentLine());
    }

    @Test
    void canDisableGlobalToolLoreWithoutLegacyFallbackReenablingIt() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tool-lore.enabled", false);
        config.set("default-lore-format.lines", List.of("legacy line"));

        PluginSettings.LoreSettings settings = PluginSettings.loreSettings(config);

        assertTrue(settings.template().isEmpty());
    }

    @Test
    void rejectsNonListLoreTemplate() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tool-lore.template", "one embedded scalar");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PluginSettings.loreSettings(config));

        assertTrue(exception.getMessage().contains("must be a YAML list"));
    }

    @Test
    void failedLoadDoesNotPartiallyMutateLiveSettings() {
        PluginSettings settings = new PluginSettings();
        YamlConfiguration valid = new YamlConfiguration();
        valid.set("settings.unauthorized-warning-cooldown-ms", 2750L);
        valid.set("progress-bar.width", 17);
        valid.set("performance.progress-visual-refresh-ticks", 6L);
        settings.load(valid);

        YamlConfiguration invalid = new YamlConfiguration();
        invalid.set("settings.unauthorized-warning-cooldown-ms", 9900L);
        invalid.set("progress-bar.width", 44);
        invalid.set("performance.progress-visual-refresh-ticks", 19L);
        // Lore validation occurs late in candidate parsing. The values above
        // must not leak into the already-active runtime settings after failure.
        invalid.set("tool-lore.template", "not-a-list");

        assertThrows(IllegalArgumentException.class, () -> settings.load(invalid));
        assertEquals(2750L, settings.warningCooldownMillis());
        assertEquals(17, settings.progressBarWidth());
        assertEquals(6L, settings.progressVisualRefreshTicks());
    }

    @Test
    void clampsCoalescedProgressRefreshWindow() {
        YamlConfiguration tooFast = new YamlConfiguration();
        tooFast.set("performance.progress-visual-refresh-ticks", 0L);
        assertEquals(1L, PluginSettings.progressVisualRefreshTicks(tooFast));

        YamlConfiguration tooSlow = new YamlConfiguration();
        tooSlow.set("performance.progress-visual-refresh-ticks", 200L);
        assertEquals(20L, PluginSettings.progressVisualRefreshTicks(tooSlow));

        assertEquals(4L, PluginSettings.progressVisualRefreshTicks(
                new YamlConfiguration()));
    }

    @Test
    void clampsRecoveredStorageAndNaturalBlockSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.max-batches-per-flush", 500);
        config.set("storage.pressure-flush-threshold", 1);
        config.set("natural-block-progression.enabled", false);
        config.set("natural-block-progression.fail-closed-while-loading", false);
        config.set("natural-block-progression.chunk-load-batch-size", 999);
        config.set("natural-block-progression.chunk-load-retry-ticks", 1L);

        assertEquals(64, PluginSettings.maxBatchesPerFlush(config));
        assertEquals(256, PluginSettings.pressureFlushThreshold(config, 256, 8192));
        PluginSettings.NaturalBlockSettings natural = PluginSettings.naturalBlockSettings(config);
        assertEquals(false, natural.enabled());
        assertEquals(false, natural.failClosed());
        assertEquals(256, natural.chunkLoadBatchSize());
        assertEquals(20L, natural.chunkLoadRetryTicks());
    }
}
