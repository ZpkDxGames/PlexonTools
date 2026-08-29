package com.plexon.tools.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public final class PlexonGuiHolder implements InventoryHolder {
    private final View view;
    private final String toolId;
    private final int page;
    private final int level;
    private final String context;
    private final UUID subjectId;
    private final String categoryId;
    private Inventory inventory;

    public PlexonGuiHolder(View view, String toolId, int page, int level) {
        this(view, toolId, page, level, null);
    }

    public PlexonGuiHolder(View view, String toolId, int page, int level, String context) {
        this(view, toolId, page, level, context, null, null);
    }

    public PlexonGuiHolder(
            View view,
            String toolId,
            int page,
            int level,
            String context,
            UUID subjectId,
            String categoryId
    ) {
        this.view = view;
        this.toolId = toolId;
        this.page = page;
        this.level = level;
        this.context = context;
        this.subjectId = subjectId;
        this.categoryId = categoryId;
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
    public String context() { return context; }
    public UUID subjectId() { return subjectId; }
    public String categoryId() { return categoryId; }

    public enum View {
        WORLD_TOOL_MENU,
        SHOWCASE,
        CATEGORY_SELECT,
        ADMIN_DASHBOARD,
        WORLD_MENUS,
        WORLD_MENU_EDITOR,
        WORLD_MENU_TOOLS,
        ADMIN_LIST,
        ADMIN_EDITOR,
        WORLDS,
        LEVELS,
        LEVEL_EDITOR,
        REQUIREMENT,
        TARGET_SELECTOR,
        TARGET_AMOUNT,
        ENCHANTMENTS,
        LORE,
        CATEGORIES,
        CATEGORY_EDITOR,
        CATEGORY_ASSIGN,
        GLOBAL_SETTINGS,
        ABILITIES
    }
}
