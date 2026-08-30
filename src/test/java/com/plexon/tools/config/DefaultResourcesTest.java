package com.plexon.tools.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DefaultResourcesTest {
    private static final List<String> CONFIG_FILES = List.of(
            "config.yml",
            "tools.yml",
            "menus.yml",
            "categories.yml",
            "messages.yml"
    );

    @Test
    void bundledConfigurationFilesLoadAsYaml() {
        CONFIG_FILES.forEach(DefaultResourcesTest::load);
    }

    @Test
    void legendaryPickaxeDefaultsRemainCrossFileCompatible() {
        YamlConfiguration config = load("config.yml");
        YamlConfiguration tools = load("tools.yml");
        YamlConfiguration menus = load("menus.yml");
        YamlConfiguration categories = load("categories.yml");
        YamlConfiguration messages = load("messages.yml");

        ConfigurationSection definitions = requireSection(tools, "tools");
        assertEquals(Set.of("legendary_pickaxe"), definitions.getKeys(false));
        assertEquals("mining", tools.getString("tools.legendary_pickaxe.category"));
        assertEquals(List.of(
                        "Survival_World",
                        "Survival_World_nether",
                        "Survival_World_the_end"),
                tools.getStringList("tools.legendary_pickaxe.allowed_worlds"));
        assertEquals("PLAYER", tools.getString(
                "tools.legendary_pickaxe.progression.scope"));
        assertEquals("Survival_World", tools.getString(
                "tools.legendary_pickaxe.progression.anchor_world"));

        ConfigurationSection levels = requireSection(tools, "tools.legendary_pickaxe.levels");
        assertEquals(100, levels.getKeys(false).size());
        for (int level = 1; level <= 100; level++) {
            assertTrue(levels.isConfigurationSection(Integer.toString(level)),
                    "Missing Legendary Pickaxe level " + level);
        }
        String finalRequirements = "tools.legendary_pickaxe.levels.100.requirements";
        assertTrue(tools.contains(finalRequirements),
                "The maximum level must explicitly opt out of inherited target requirements");
        assertTrue(requireSection(tools, finalRequirements).getKeys(false).isEmpty(),
                "The maximum level must not track additional progression targets");

        assertTrue(categories.isConfigurationSection("categories.mining"));
        assertTrue(menus.isConfigurationSection(
                "worlds.survival_world.tools.legendary_pickaxe"));
        assertTrue(config.getBoolean("world-menu.auto-show-allowed-tools"));
        assertTrue(config.getBoolean("world-menu.toggle-panel.enabled"));
        assertTrue(config.getBoolean("effects.progress-action-bar"));
        assertEquals("plexontools.db", config.getString("storage.database-file"));
        assertEquals(20L, config.getLong("storage.flush-interval-ticks"));
        assertEquals(4L, config.getLong(
                "performance.progress-visual-refresh-ticks"));
        assertTrue(config.getBoolean("storage.integrity-check-on-startup"));
        assertTrue(config.getBoolean("tool-lore.enabled"));
        assertFalse(config.getStringList("tool-lore.template").isEmpty());
        assertTrue(config.getStringList("tool-lore.template").contains("{requirement_lines}"));
        assertFalse(config.getString("tool-lore.requirements.general-line", "").isBlank());
        assertFalse(config.getString("tool-lore.requirements.specific-line", "").isBlank());
        assertFalse(config.getString("tool-lore.requirements.maximum-line", "").isBlank());
        assertFalse(config.getStringList("world-menu.tool-card.lore").isEmpty());
        assertFalse(messages.getString("messages.progress-update", "").isBlank());
        assertFalse(messages.getString("messages.backup-complete", "").isBlank());
    }

    @Test
    void runtimeRegistryIsNotBundledAsDefaultPlayerData() {
        assertNull(DefaultResourcesTest.class.getResourceAsStream("/data.yml"));
        assertNull(DefaultResourcesTest.class.getResourceAsStream("/plexontools.db"));
    }

    private static YamlConfiguration load(String name) {
        InputStream stream = DefaultResourcesTest.class.getResourceAsStream("/" + name);
        assertNotNull(stream, () -> "Missing bundled resource " + name);
        try (stream; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            throw new AssertionError("Could not load bundled resource " + name, exception);
        }
    }

    private static ConfigurationSection requireSection(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        assertNotNull(section, () -> "Missing configuration section " + path);
        return section;
    }
}
