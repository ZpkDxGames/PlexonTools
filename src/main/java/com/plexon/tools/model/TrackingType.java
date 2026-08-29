package com.plexon.tools.model;

import java.util.Locale;

public enum TrackingType {
    BLOCKS_BROKEN("Blocks broken", "Break", "blocks", TargetKind.BLOCK),
    MOBS_KILLED("Mobs killed", "Kill", "mobs", TargetKind.ENTITY),
    ITEMS_FARMED("Items farmed", "Harvest", "crops", TargetKind.CROP),
    FISH_CAUGHT("Fish caught", "Catch", "fish", TargetKind.FISH),
    DAMAGE_DEALT("Damage dealt", "Damage", "entities", TargetKind.ENTITY),
    BLOCKS_PLACED("Blocks placed", "Place", "blocks", TargetKind.BLOCK);

    private final String displayName;
    private final String action;
    private final String noun;
    private final TargetKind targetKind;

    TrackingType(String displayName, String action, String noun, TargetKind targetKind) {
        this.displayName = displayName;
        this.action = action;
        this.noun = noun;
        this.targetKind = targetKind;
    }

    public String displayName() {
        return displayName;
    }

    public String action() {
        return action;
    }

    public String noun() {
        return noun;
    }

    public TargetKind targetKind() {
        return targetKind;
    }

    public boolean usesEntityTargets() {
        return targetKind == TargetKind.ENTITY;
    }

    public boolean usesMaterialTargets() {
        return targetKind != TargetKind.ENTITY;
    }

    public TrackingType next() {
        TrackingType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static TrackingType parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public enum TargetKind {
        BLOCK,
        ENTITY,
        CROP,
        FISH
    }
}
