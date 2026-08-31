#!/usr/bin/env python3
"""Regenerate the bundled four-relic profile without touching custom files."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "src/main/resources/tools.yml"
WORLDS = ("Survival_World", "Survival_World_nether", "Survival_World_the_end")


def stage(level: int) -> int:
    if level < 10:
        return 0
    if level < 25:
        return 1
    if level < 50:
        return 2
    if level < 75:
        return 3
    return 4


def material(level: int, suffix: str) -> str:
    return ("WOODEN", "STONE", "IRON", "DIAMOND", "NETHERITE")[stage(level)] + "_" + suffix


def display_name(kind: str, level: int, icon: str, gradients: tuple[tuple[str, str], ...]) -> str:
    left, right = gradients[stage(level)]
    return (
        f'<!italic><#FFD54F>{icon}</#FFD54F> '
        f'<gradient:{left}:{right}><bold>Legendary {kind}</bold></gradient> '
        f'<dark_gray>•</dark_gray> <gray>Lv. {level}</gray>'
    )


def pair(targets: tuple[str, ...], level: int) -> tuple[str, str]:
    first_index = (level - 1) % len(targets)
    second_index = (level * 3 + stage(level)) % len(targets)
    if second_index == first_index:
        second_index = (second_index + 1) % len(targets)
    return targets[first_index], targets[second_index]


def append_header(lines: list[str], tool_id: str, category: str, kind: str,
                  base: str, tracking: str, targets: dict[str, int]) -> None:
    lines.extend([
        f"  {tool_id}:",
        "    enabled: true",
        f"    category: {category}",
        f'    display_name: "{display_for(kind, 1)}"',
        f"    base_material: {base}",
        "    allowed_worlds:",
        *(f"      - {world}" for world in WORLDS),
        "    progression:",
        "      scope: PLAYER",
        "      anchor_world: Survival_World",
        "    tracking:",
        f"      type: {tracking}",
        "      mode: SPECIFIC",
        "      targets:",
        *(f"        {target}: {amount}" for target, amount in targets.items()),
        "    levels:",
    ])


GRADIENTS = {
    "Sword": (("#EF9A9A", "#E53935"), ("#B0BEC5", "#78909C"),
              ("#ECEFF1", "#90A4AE"), ("#80DEEA", "#0288D1"),
              ("#CE93D8", "#7B1FA2")),
    "Axe": (("#A1887F", "#6D4C41"), ("#BDBDBD", "#757575"),
            ("#ECEFF1", "#90A4AE"), ("#80CBC4", "#00897B"),
            ("#B39DDB", "#673AB7")),
    "Shovel": (("#BCAAA4", "#795548"), ("#BDBDBD", "#757575"),
               ("#ECEFF1", "#90A4AE"), ("#81D4FA", "#0288D1"),
               ("#B39DDB", "#673AB7")),
}


def display_for(kind: str, level: int) -> str:
    icon = {"Sword": "⚔", "Axe": "🪓", "Shovel": "♠"}[kind]
    return display_name(kind, level, icon, GRADIENTS[kind])


def append_enchantments(lines: list[str], kind: str, level: int) -> None:
    lines.append("        enchantments:")
    if kind == "Sword":
        lines.append(f"          SHARPNESS: {min(10, 1 + level // 10)}")
        lines.append(f"          UNBREAKING: {min(7, 1 + level // 18)}")
        if level >= 25:
            lines.append(f"          LOOTING: {min(5, 1 + (level - 25) // 25)}")
        if level >= 50:
            lines.append(f"          FIRE_ASPECT: {min(3, 1 + (level - 50) // 25)}")
    else:
        lines.append(f"          EFFICIENCY: {min(10, 1 + level // 8)}")
        lines.append(f"          UNBREAKING: {min(7, 1 + level // 18)}")
        if level >= 25:
            lines.append(f"          FORTUNE: {min(5, 1 + (level - 25) // 25)}")
        if kind == "Axe" and level >= 50:
            lines.append(f"          SHARPNESS: {min(5, 1 + (level - 50) // 13)}")


def append_abilities(lines: list[str], level: int, block_tool: bool) -> None:
    if level < 10:
        return
    lines.extend([
        "        abilities:",
        "          EXP_BOOSTER:",
        f"            multiplier: {1.0 + min(10, level // 10) * 0.1:.2f}",
    ])
    if block_tool and level >= 50:
        lines.extend(["          MAGNET:", "            enabled: true"])
    if block_tool and level >= 75:
        lines.extend(["          AREA_MINE_3X3:", "            enabled: true"])


def append_level(lines: list[str], kind: str, level: int,
                 requirements: dict[str, int], suffix: str, block_tool: bool) -> None:
    lines.extend([
        f"      {level}:",
        "        requirement_mode: SPECIFIC",
    ])
    if level == 100:
        lines.append("        requirements: {}")
    else:
        lines.append("        requirements:")
        lines.extend(f"          {target}: {amount}" for target, amount in requirements.items())
    lines.append(f'        display_name: "{display_for(kind, level)}"')
    if level in (10, 25, 50, 75):
        lines.append(f"        material_upgrade: {material(level, suffix)}")
    append_enchantments(lines, kind, level)
    append_abilities(lines, level, block_tool)
    lines.extend(["        item:", "          glint: AUTO", ""])


def sword_profile() -> str:
    lines: list[str] = []
    starter = {"CHICKEN": 15, "PIG": 15, "SHEEP": 15, "COW": 15}
    append_header(lines, "legendary_sword", "combat", "Sword", "WOODEN_SWORD",
                  "MOBS_KILLED", starter)
    stages = (
        ("CHICKEN", "PIG", "SHEEP", "COW", "RABBIT"),
        ("ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "DROWNED"),
        ("HUSK", "STRAY", "SLIME", "WITCH", "PILLAGER"),
        ("ZOMBIFIED_PIGLIN", "PIGLIN", "HOGLIN", "BLAZE", "MAGMA_CUBE"),
        ("ENDERMAN", "SHULKER", "PHANTOM", "WITHER_SKELETON", "RAVAGER"),
    )
    for level in range(1, 101):
        if level == 1:
            requirements = starter
        elif level == 100:
            requirements = {}
        else:
            first, second = pair(stages[stage(level)], level)
            requirements = {
                first: 12 + level * 3,
                second: 8 + level * 2,
            }
        append_level(lines, "Sword", level, requirements, "SWORD", False)
    return "\n".join(lines).rstrip()


def axe_profile() -> str:
    lines: list[str] = []
    stages = (
        ("OAK_LOG", "OAK_PLANKS", "BIRCH_LOG", "SPRUCE_LOG"),
        ("BIRCH_LOG", "SPRUCE_LOG", "JUNGLE_LOG", "ACACIA_LOG", "DARK_OAK_LOG"),
        ("MANGROVE_LOG", "CHERRY_LOG", "OAK_WOOD", "BIRCH_WOOD", "JUNGLE_WOOD"),
        ("CRIMSON_STEM", "WARPED_STEM", "CRIMSON_HYPHAE", "WARPED_HYPHAE"),
        ("DARK_OAK_WOOD", "MANGROVE_WOOD", "CHERRY_WOOD", "CRIMSON_STEM", "WARPED_STEM"),
    )
    starter = {"OAK_LOG": 160, "OAK_PLANKS": 64}
    append_header(lines, "legendary_axe", "foraging", "Axe", "WOODEN_AXE",
                  "BLOCKS_BROKEN", starter)
    for level in range(1, 101):
        if level == 100:
            requirements = {}
        else:
            first, second = pair(stages[stage(level)], level)
            requirements = {
                first: 160 + level * 65,
                second: 40 + level * 18,
            }
        append_level(lines, "Axe", level, requirements, "AXE", True)
    return "\n".join(lines).rstrip()


def shovel_profile() -> str:
    lines: list[str] = []
    stages = (
        ("DIRT", "SAND", "GRAVEL", "CLAY"),
        ("COARSE_DIRT", "ROOTED_DIRT", "SAND", "RED_SAND", "GRAVEL"),
        ("CLAY", "MUD", "SNOW_BLOCK", "PODZOL", "MYCELIUM"),
        ("SOUL_SAND", "SOUL_SOIL", "GRAVEL", "MUD", "CLAY"),
        ("SNOW_BLOCK", "SOUL_SAND", "SOUL_SOIL", "RED_SAND", "MUD"),
    )
    starter = {"DIRT": 200, "SAND": 64}
    append_header(lines, "legendary_shovel", "excavation", "Shovel", "WOODEN_SHOVEL",
                  "BLOCKS_BROKEN", starter)
    for level in range(1, 101):
        if level == 100:
            requirements = {}
        else:
            first, second = pair(stages[stage(level)], level)
            requirements = {
                first: 200 + level * 75,
                second: 48 + level * 20,
            }
        append_level(lines, "Shovel", level, requirements, "SHOVEL", True)
    return "\n".join(lines).rstrip()


def fixed_pickaxe(source: str) -> str:
    start = source.index("  legendary_pickaxe:")
    next_tool = source.find("\n  legendary_", start + 3)
    end = len(source) if next_tool < 0 else next_tool
    profile = source[start:end].rstrip()
    replacements = {
        "          STONE: 400\n          COPPER_ORE: 20":
            "          STONE: 400\n          GRANITE: 96",
        "          STONE: 550\n          COPPER_ORE: 28":
            "          STONE: 550\n          DIORITE: 112",
        "          STONE: 650\n          IRON_ORE: 16":
            "          STONE: 650\n          ANDESITE: 128",
        "          STONE: 875\n          IRON_ORE: 24":
            "          STONE: 875\n          TUFF: 144",
        "          STONE: 1000\n          COPPER_ORE: 48":
            "          STONE: 1000\n          DEEPSLATE: 192",
        "          DEEPSLATE: 1750\n          LAPIS_ORE: 20":
            "          DEEPSLATE: 1750\n          COPPER_ORE: 56",
        "          DEEPSLATE: 2350\n          DEEPSLATE_LAPIS_ORE: 28":
            "          DEEPSLATE: 2350\n          DEEPSLATE_COPPER_ORE: 64",
        "          DEEPSLATE: 2500\n          DEEPSLATE_REDSTONE_ORE: 64":
            "          DEEPSLATE: 2500\n          DEEPSLATE_IRON_ORE: 56",
        "          EXP_BOOSTER:\n            multiplier: 1.10\n            multiplier: 1.10":
            "          EXP_BOOSTER:\n            multiplier: 1.10",
    }
    for old, new in replacements.items():
        if old in profile:
            profile = profile.replace(old, new, 1)
        elif new not in profile:
            raise RuntimeError(f"Expected pickaxe profile fragment was not found: {old!r}")
    return profile


def main() -> None:
    source = TOOLS.read_text(encoding="utf-8")
    marker = "\ntools:\n"
    header, _ = source.split(marker, 1)
    output = "\n\n".join((
        header.rstrip() + "\n\ntools:",
        sword_profile(),
        fixed_pickaxe(source),
        axe_profile(),
        shovel_profile(),
    )) + "\n"
    TOOLS.write_text(output, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
