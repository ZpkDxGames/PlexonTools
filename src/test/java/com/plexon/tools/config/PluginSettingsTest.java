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

        PluginSettings.LoreSettings settings = PluginSettings.loreSettings(config);

        assertEquals(List.of(
                "<gold>{owner_name}</gold>",
                "{requirement_lines}",
                "<gray>{progress_bar}</gray>"), settings.template());
        assertEquals("GENERAL {requirement_current}", settings.generalRequirementLine());
        assertEquals("SPECIFIC {requirement_target}", settings.specificRequirementLine());
        assertEquals("MAXIMUM", settings.maximumRequirementLine());
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
}
