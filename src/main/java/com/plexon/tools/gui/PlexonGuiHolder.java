package com.plexon.tools.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class PlexonGuiHolder implements InventoryHolder {
    private final View view;
    private final String toolId;
    private final int page;
    private final int level;
    private Inventory inventory;

    public PlexonGuiHolder(View view, String toolId, int page, int level) {
        this.view = view;
        this.toolId = toolId;
        this.page = page;
        this.level = level;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "GUI inventory has not been attached yet");
    }

    public View view() { return view; }
    public String toolId() { return toolId; }
    public int page() { return page; }
    public int level() { return level; }

    public enum View {
        SHOWCASE,
        ADMIN_LIST,
        ADMIN_EDITOR,
        WORLDS,
        LEVELS,
        LEVEL_EDITOR
    }
}
