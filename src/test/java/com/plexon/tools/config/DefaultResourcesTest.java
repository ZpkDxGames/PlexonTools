package com.plexon.tools.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DefaultResourcesTest {
    private static final List<String> CONFIG_FILES = List.of(
            "config.yml", "tools.yml", "menus.yml", "categories.yml", "messages.yml");
    private static final List<String> WORLDS = List.of(
            "Survival_World", "Survival_World_nether", "Survival_World_the_end");
    private static final Set<String> TOOL_IDS = Set.of(
            "legendary_sword", "legendary_pickaxe", "legendary_axe", "legendary_shovel");

    @Test
    void bundledConfigurationFilesLoadAsYaml() {
        CONFIG_FILES.forEach(DefaultResourcesTest::load);
    }

    @Test
    void bundledRelicsProvideFourCompleteCrossWorldProfiles() {
        YamlConfiguration tools = load("tools.yml");
        ConfigurationSection definitions = requireSection(tools, "tools");
        assertEquals(TOOL_IDS, definitions.getKeys(false));

        Map<String, String> categories = Map.of(
                "legendary_sword", "combat",
                "legendary_pickaxe", "mining",
                "legendary_axe", "foraging",
                "legendary_shovel", "excavation");
        categories.forEach((toolId, category) -> {
            String root = "tools." + toolId;
            assertTrue(tools.getBoolean(root + ".enabled"));
            assertEquals(category, tools.getString(root + ".category"));
            assertEquals(WORLDS, tools.getStringList(root + ".allowed_worlds"));
            assertEquals("PLAYER", tools.getString(root + ".progression.scope"));
            assertEquals("Survival_World", tools.getString(root + ".progression.anchor_world"));

            ConfigurationSection levels = requireSection(tools, root + ".levels");
            assertEquals(100, levels.getKeys(false).size());
            for (int level = 1; level <= 100; level++) {
                assertTrue(levels.isConfigurationSection(Integer.toString(level)),
                        "Missing " + toolId + " level " + level);
            }
            String terminal = root + ".levels.100.requirements";
            assertTrue(tools.contains(terminal));
            assertTrue(requireSection(tools, terminal).getKeys(false).isEmpty(),
                    () -> toolId + " level 100 must be terminal");
        });
    }

    @Test
    void everyBlockObjectiveAndMaterialNameResolves() {
        YamlConfiguration tools = load("tools.yml");
        for (String toolId : List.of("legendary_pickaxe", "legendary_axe", "legendary_shovel")) {
            String root = "tools." + toolId;
            Material currentMaterial = requireMaterial(tools.getString(root + ".base_material"));
            for (int level = 1; level <= 100; level++) {
                String levelRoot = root + ".levels." + level;
                String upgrade = tools.getString(levelRoot + ".material",
                        tools.getString(levelRoot + ".material_upgrade"));
                if (upgrade != null && !upgrade.isBlank()) {
                    currentMaterial = requireMaterial(upgrade);
                }
                ConfigurationSection requirements = requireSection(
                        tools, levelRoot + ".requirements");
                for (String target : requirements.getKeys(false)) {
                    requireMaterial(target);
                }
            }
        }
    }

    @Test
    void survivalMenusUseRequestedFourRelicOrder() {
        YamlConfiguration menus = load("menus.yml");
        Map<String, Integer> slots = Map.of(
                "legendary_sword", 10,
                "legendary_pickaxe", 12,
                "legendary_axe", 14,
                "legendary_shovel", 16);
        for (String world : List.of(
                "survival_world", "survival_world_nether", "survival_world_the_end")) {
            assertEquals(3, menus.getInt("worlds." + world + ".rows"));
            slots.forEach((toolId, slot) -> assertEquals(slot,
                    menus.getInt("worlds." + world + ".tools." + toolId + ".slot")));
        }
    }

    @Test
    void categoriesAndCompactDynamicLoreRemainCrossFileCompatible() {
        YamlConfiguration config = load("config.yml");
        YamlConfiguration categories = load("categories.yml");
        YamlConfiguration messages = load("messages.yml");

        assertEquals(Set.of("combat", "mining", "foraging", "excavation"),
                requireSection(categories, "categories").getKeys(false));
        assertEquals(16, config.getInt("progress-bar.width"));
        assertEquals("#FF5252", config.getString("progress-value-colors.start"));
        assertEquals("#FFD740", config.getString("progress-value-colors.middle"));
        assertEquals("#76FF03", config.getString("progress-value-colors.complete"));

        List<String> lore = config.getStringList("tool-lore.template");
        String joined = String.join("\n", lore);
        assertTrue(joined.contains("ENCHANTMENTS"));
        assertTrue(joined.contains("OBJECTIVES"));
        assertTrue(lore.contains("{enchantment_lines}"));
        assertTrue(lore.contains("{requirement_lines}"));
        assertFalse(joined.contains("TRIAL OBJECTIVES"));
        assertFalse(joined.contains("UPGRADE OBJECTIVES"));
        assertFalse(joined.contains("Realm Binding"));

        String specific = config.getString("tool-lore.requirements.specific-line", "");
        assertTrue(specific.contains("{requirement_current_color}"));
        assertTrue(specific.contains("#B0BEC5"));
        String card = String.join("\n", config.getStringList("world-menu.tool-card.lore"));
        assertTrue(card.contains("{current_color}"));
        assertTrue(card.contains("{percentage_color}"));
        assertTrue(messages.getString("messages.progress-update", "")
                .contains("{current_color}"));
        assertTrue(messages.getString("messages.reload-complete", "")
                .contains("{version}"));
        assertTrue(config.getBoolean("world-menu.auto-show-allowed-tools"));
        assertTrue(config.getBoolean("world-menu.toggle-panel.enabled"));
        assertEquals("plexontools.db", config.getString("storage.database-file"));
    }

    @Test
    void runtimeRegistryIsNotBundledAsDefaultPlayerData() {
        assertNull(DefaultResourcesTest.class.getResourceAsStream("/data.yml"));
        assertNull(DefaultResourcesTest.class.getResourceAsStream("/plexontools.db"));
    }

    private static Material requireMaterial(String name) {
        Material material = Material.matchMaterial(name == null ? "" : name);
        assertNotNull(material, () -> "Unknown material " + name);
        return material;
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
