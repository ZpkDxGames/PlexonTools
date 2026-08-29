package com.plexon.tools.model;

import java.util.Locale;

public enum ToolAbilityType {
    AUTO_SMELT("Auto Smelt", "Smelts supported ore drops automatically."),
    AREA_MINE_3X3("3×3 Area Mine", "Breaks a protected-aware 3×3 plane."),
    EXP_BOOSTER("EXP Booster", "Multiplies experience earned with the tool."),
    MOB_POTION_EFFECT("Potion Effect", "Applies a configured effect to the holder or hit target."),
    MAGNET("Magnet", "Moves eligible drops directly into the inventory.");

    private final String displayName;
    private final String description;

    ToolAbilityType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public static ToolAbilityType parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
