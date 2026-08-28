package com.plexon.tools.gui;

import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.model.ToolLevel;
import com.plexon.tools.model.TrackingType;
import com.plexon.tools.service.ChatPromptService;
import com.plexon.tools.service.ToolGrantService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class GuiManager implements Listener {
    private static final int[] GRID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final ToolConfigRepository tools;
    private final ToolItemService itemService;
    private final ToolGrantService grants;
    private final ChatPromptService prompts;
    private final PluginSettings settings;
    private final MessageService messages;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;
    private final Map<UUID, PendingDelete> pendingDeletes = new HashMap<>();

    public GuiManager(
            JavaPlugin plugin,
            ToolConfigRepository tools,
            ToolItemService itemService,
            ToolGrantService grants,
            ChatPromptService prompts,
            PluginSettings settings,
            MessageService messages
    ) {
        this.tools = tools;
        this.itemService = itemService;
        this.grants = grants;
        this.prompts = prompts;
        this.settings = settings;
        this.messages = messages;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.valueKey = new NamespacedKey(plugin, "gui_value");
    }

    public void openShowcase(Player player, int requestedPage) {
        int rows = settings.showcaseRows();
        int size = rows * 9;
        int[] slots = contentSlots(rows);
        List<ToolDefinition> visible = tools.all().stream()
                .filter(ToolDefinition::enabled)
                .filter(tool -> settings.showLockedTools() || tool.isAllowedWorld(player.getWorld().getName()))
                .sorted(Comparator.comparing(ToolDefinition::id))
                .toList();
        int pages = pageCount(visible.size(), slots.length);
        int page = clampPage(requestedPage, pages);

        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.SHOWCASE, null, page, 0);
        Inventory inventory = Bukkit.createInventory(holder, size,
                messages.parse(settings.showcaseTitle() + " <dark_gray>• " + (page + 1) + "/" + pages + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);

        int offset = page * slots.length;
        for (int index = 0; index < slots.length && offset + index < visible.size(); index++) {
            ToolDefinition definition = visible.get(offset + index);
            inventory.setItem(slots[index], showcaseIcon(player, definition));
        }
        addNavigation(inventory, page, pages, "showcase-page");
        inventory.setItem(size - 5, button("close", "", Material.BARRIER,
                "<red><bold>Close</bold></red>", "<gray>Close this menu.</gray>"));
        player.openInventory(inventory);
    }

    public void openAdminList(Player player, int requestedPage) {
        if (!requireAdmin(player)) {
            return;
        }
        List<ToolDefinition> definitions = tools.all().stream()
                .sorted(Comparator.comparing(ToolDefinition::id))
                .toList();
        int pages = pageCount(definitions.size(), GRID_SLOTS.length);
        int page = clampPage(requestedPage, pages);

        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.ADMIN_LIST, null, page, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse(settings.adminTitle() + " <dark_gray>• " + (page + 1) + "/" + pages + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);

        int offset = page * GRID_SLOTS.length;
        for (int index = 0; index < GRID_SLOTS.length && offset + index < definitions.size(); index++) {
            ToolDefinition definition = definitions.get(offset + index);
            inventory.setItem(GRID_SLOTS[index], adminToolIcon(definition));
        }
        addNavigation(inventory, page, pages, "admin-page");
        inventory.setItem(45, button("showcase", "", Material.COMPASS,
                "<aqua><bold>Player showcase</bold></aqua>", "<gray>Preview the player-facing menu.</gray>"));
        inventory.setItem(49, button("create", "", Material.LIME_DYE,
                "<green><bold>Create a tool</bold></green>",
                "<gray>Uses your held item's material,</gray>", "<gray>or an iron pickaxe if your hand is empty.</gray>"));
        inventory.setItem(53, button("close", "", Material.BARRIER,
                "<red><bold>Close</bold></red>", "<gray>Close this menu.</gray>"));
        player.openInventory(inventory);
    }

    private void openEditor(Player player, String toolId) {
        if (!requireAdmin(player)) {
            return;
        }
        ToolDefinition tool = tools.find(toolId).orElse(null);
        if (tool == null) {
            openAdminList(player, 0);
            return;
        }
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.ADMIN_EDITOR, tool.id(), 0, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<gradient:#4158D0:#C850C0><bold>Edit</bold></gradient> <dark_gray>•</dark_gray> " + tool.displayName()));
        holder.attach(inventory);
        fill(inventory);

        inventory.setItem(10, button("toggle", tool.id(), tool.enabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                tool.enabled() ? "<green><bold>Enabled</bold></green>" : "<gray><bold>Disabled</bold></gray>",
                "<gray>Click to toggle player access and progression.</gray>"));
        inventory.setItem(11, button("display-name", tool.id(), Material.NAME_TAG,
                "<yellow><bold>Display name</bold></yellow>", tool.displayName(), "", "<gray>Click to edit in chat.</gray>"));
        inventory.setItem(12, button("base-material", tool.id(), Material.ANVIL,
                "<gold><bold>Base material</bold></gold>", "<white>" + tool.baseMaterial().name() + "</white>", "",
                "<gray>Click with an item on your cursor</gray>", "<gray>or hold an item in your main hand.</gray>"));
        inventory.setItem(13, button("worlds", tool.id(), Material.ENDER_EYE,
                "<aqua><bold>Allowed worlds</bold></aqua>",
                "<white>" + String.join(", ", tool.allowedWorlds()) + "</white>", "", "<gray>Click to manage world access.</gray>"));
        inventory.setItem(14, button("tracking-type", tool.id(), Material.TARGET,
                "<light_purple><bold>Tracking type</bold></light_purple>",
                "<white>" + tool.trackingType().displayName() + "</white>", "", "<gray>Click to cycle. Changing type clears targets.</gray>"));
        inventory.setItem(15, button("tracking-targets", tool.id(), Material.WRITABLE_BOOK,
                "<blue><bold>Tracking targets</bold></blue>",
                "<white>" + summarize(tool.targetNames().stream().sorted().toList(), 5) + "</white>", "",
                "<gray>Click to enter a comma-separated list.</gray>", "<dark_gray>Empty / none means every target.</dark_gray>"));
        inventory.setItem(16, button("levels", tool.id(), Material.EXPERIENCE_BOTTLE,
                "<green><bold>Progression levels</bold></green>",
                "<white>" + tool.levels().size() + " configured level(s)</white>", "", "<gray>Click to edit rewards and thresholds.</gray>"));
        inventory.setItem(31, previewIcon(tool));
        inventory.setItem(45, button("admin-list", "", Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to all tools.</gray>"));
        inventory.setItem(49, button("give-self", tool.id(), Material.CHEST,
                "<green><bold>Give to yourself</bold></green>",
                "<gray>Creates a unique instance bound</gray>", "<gray>to your current world.</gray>"));
        inventory.setItem(53, button("delete", tool.id(), Material.RED_DYE,
                "<red><bold>Delete tool</bold></red>",
                "<gray>Shift-right-click twice to delete.</gray>", "<dark_red>This cannot be undone.</dark_red>"));
        player.openInventory(inventory);
    }

    private void openWorlds(Player player, String toolId) {
        ToolDefinition tool = tools.find(toolId).orElse(null);
        if (tool == null || !requireAdmin(player)) {
            openAdminList(player, 0);
            return;
        }
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.WORLDS, tool.id(), 0, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<aqua><bold>World access</bold></aqua> <dark_gray>•</dark_gray> " + tool.displayName()));
        holder.attach(inventory);
        fill(inventory);

        List<World> worlds = Bukkit.getWorlds().stream().sorted(Comparator.comparing(World::getName)).toList();
        for (int index = 0; index < GRID_SLOTS.length && index < worlds.size(); index++) {
            World world = worlds.get(index);
            boolean allowed = tool.isAllowedWorld(world.getName());
            Material material = switch (world.getEnvironment()) {
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> Material.GRASS_BLOCK;
            };
            inventory.setItem(GRID_SLOTS[index], button("toggle-world", world.getName(), material,
                    allowed ? "<green><bold>✓ " + messages.plain(world.getName()) + "</bold></green>"
                            : "<red><bold>✕ " + messages.plain(world.getName()) + "</bold></red>",
                    allowed ? "<gray>Allowed. Click to lock.</gray>" : "<gray>Locked. Click to allow.</gray>"));
        }
        inventory.setItem(45, button("editor", tool.id(), Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to the tool editor.</gray>"));
        inventory.setItem(49, button("add-world", tool.id(), Material.ENDER_PEARL,
                "<aqua><bold>Add unloaded world</bold></aqua>",
                "<gray>Enter a world name manually.</gray>"));
        player.openInventory(inventory);
    }

    private void openLevels(Player player, String toolId, int requestedPage) {
        ToolDefinition tool = tools.find(toolId).orElse(null);
        if (tool == null || !requireAdmin(player)) {
            openAdminList(player, 0);
            return;
        }
        List<ToolLevel> levels = new ArrayList<>(tool.levels().values());
        int pages = pageCount(levels.size(), GRID_SLOTS.length);
        int page = clampPage(requestedPage, pages);
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.LEVELS, tool.id(), page, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<green><bold>Levels</bold></green> <dark_gray>•</dark_gray> " + tool.displayName()));
        holder.attach(inventory);
        fill(inventory);

        int offset = page * GRID_SLOTS.length;
        for (int index = 0; index < GRID_SLOTS.length && offset + index < levels.size(); index++) {
            ToolLevel level = levels.get(offset + index);
            Material icon = level.materialUpgrade() == null ? tool.baseMaterial() : level.materialUpgrade();
            inventory.setItem(GRID_SLOTS[index], button("edit-level", Integer.toString(level.number()), icon,
                    "<gradient:#41E296:#A8FF78><bold>Level " + level.number() + "</bold></gradient>",
                    "<gray>Next threshold:</gray> <white>" + level.requirement() + "</white>",
                    "<gray>Enchantments:</gray> <white>" + formatEnchantments(level.enchantments()) + "</white>",
                    "<gray>Material:</gray> <white>" + (level.materialUpgrade() == null ? "unchanged" : level.materialUpgrade().name()) + "</white>",
                    "", "<yellow>Click to edit.</yellow>"));
        }
        addNavigation(inventory, page, pages, "levels-page");
        inventory.setItem(45, button("editor", tool.id(), Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to the tool editor.</gray>"));
        inventory.setItem(49, button("add-level", tool.id(), Material.LIME_DYE,
                "<green><bold>Add level</bold></green>",
                "<gray>Clones the current final level and</gray>", "<gray>doubles its next threshold.</gray>"));
        player.openInventory(inventory);
    }

    private void openLevelEditor(Player player, String toolId, int levelNumber) {
        ToolDefinition tool = tools.find(toolId).orElse(null);
        ToolLevel level = tool == null ? null : tool.level(levelNumber).orElse(null);
        if (tool == null || level == null || !requireAdmin(player)) {
            openAdminList(player, 0);
            return;
        }
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.LEVEL_EDITOR, tool.id(), 0, level.number());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<green><bold>Level " + level.number() + "</bold></green> <dark_gray>•</dark_gray> " + tool.displayName()));
        holder.attach(inventory);
        fill(inventory);

        inventory.setItem(10, button("level-requirement", tool.id(), Material.CLOCK,
                "<yellow><bold>Next threshold</bold></yellow>", "<white>" + level.requirement() + " events</white>", "",
                "<gray>Progress needed to advance from</gray>", "<gray>this level to the next one.</gray>"));
        inventory.setItem(11, button("level-enchantments", tool.id(), Material.ENCHANTED_BOOK,
                "<light_purple><bold>Enchantments</bold></light_purple>",
                "<white>" + formatEnchantments(level.enchantments()) + "</white>", "",
                "<gray>Format: efficiency=2, unbreaking=1</gray>"));
        inventory.setItem(12, button("level-lore", tool.id(), Material.BOOK,
                "<aqua><bold>Lore</bold></aqua>", "<white>" + level.lore().size() + " line(s)</white>", "",
                "<gray>Separate lines with <white>;;</white>.</gray>"));
        inventory.setItem(13, button("level-material", tool.id(), Material.SMITHING_TABLE,
                "<gold><bold>Material upgrade</bold></gold>",
                "<white>" + (level.materialUpgrade() == null ? "No change" : level.materialUpgrade().name()) + "</white>", "",
                "<gray>Click with an item to set it.</gray>", "<gray>Right-click to clear.</gray>"));
        inventory.setItem(31, previewIcon(tool, level));
        inventory.setItem(45, button("levels", tool.id(), Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to all levels.</gray>"));
        if (level.number() > 1 && level.number() == tool.maxLevel()) {
            inventory.setItem(49, button("remove-level", tool.id(), Material.RED_DYE,
                    "<red><bold>Remove final level</bold></red>",
                    "<gray>Shift-right-click to remove level " + level.number() + ".</gray>"));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PlexonGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        String action = tag(clicked, actionKey);
        String value = tag(clicked, valueKey);
        if (action == null) {
            return;
        }
        if (holder.view() != PlexonGuiHolder.View.SHOWCASE && !requireAdmin(player)) {
            return;
        }

        switch (action) {
            case "close" -> player.closeInventory();
            case "showcase" -> openShowcase(player, 0);
            case "showcase-page" -> openShowcase(player, parseInt(value, 0));
            case "admin-list" -> openAdminList(player, 0);
            case "admin-page" -> openAdminList(player, parseInt(value, 0));
            case "editor" -> openEditor(player, value);
            case "create" -> createTool(player);
            case "toggle" -> change(player, value, "enabled status",
                    () -> tools.setEnabled(value, !tools.find(value).orElseThrow().enabled()),
                    () -> openEditor(player, value));
            case "display-name" -> promptDisplayName(player, value);
            case "base-material" -> setBaseMaterial(player, value, event);
            case "worlds" -> openWorlds(player, value);
            case "tracking-type" -> change(player, value, "tracking type",
                    () -> tools.setTrackingType(value, tools.find(value).orElseThrow().trackingType().next()),
                    () -> openEditor(player, value));
            case "tracking-targets" -> promptTargets(player, value);
            case "levels" -> openLevels(player, value, 0);
            case "levels-page" -> openLevels(player, holder.toolId(), parseInt(value, 0));
            case "give-self" -> giveSelf(player, value);
            case "delete" -> deleteTool(player, value, event);
            case "toggle-world" -> change(player, holder.toolId(), "allowed worlds",
                    () -> tools.toggleWorld(holder.toolId(), value),
                    () -> openWorlds(player, holder.toolId()));
            case "add-world" -> promptWorld(player, value);
            case "edit-level" -> openLevelEditor(player, holder.toolId(), parseInt(value, 1));
            case "add-level" -> addLevel(player, value);
            case "level-requirement" -> promptRequirement(player, holder.toolId(), holder.level());
            case "level-enchantments" -> promptEnchantments(player, holder.toolId(), holder.level());
            case "level-lore" -> promptLore(player, holder.toolId(), holder.level());
            case "level-material" -> setLevelMaterial(player, holder.toolId(), holder.level(), event);
            case "remove-level" -> removeLevel(player, holder.toolId(), holder.level(), event);
            default -> {
                // Non-interactive showcase and preview items intentionally do nothing.
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PlexonGuiHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    private void createTool(Player player) {
        Material selected = player.getInventory().getItemInMainHand().getType();
        if (selected.isAir()) {
            selected = Material.IRON_PICKAXE;
        }
        Material material = selected;
        prompts.begin(player,
                messages.parse("<gradient:#4158D0:#C850C0><bold>New tool ID</bold></gradient> <gray>Use lowercase letters, numbers, <white>_</white>, or <white>-</white>.</gray>"),
                input -> {
                    try {
                        ToolDefinition created = tools.createTool(input, material, player.getWorld().getName());
                        messages.send(player, "editor-saved", Map.of("field", "new tool", "tool", created.displayName()));
                        openEditor(player, created.id());
                    } catch (Exception exception) {
                        showError(player, exception);
                        openAdminList(player, 0);
                    }
                },
                () -> openAdminList(player, 0));
    }

    private void promptDisplayName(Player player, String toolId) {
        prompts.begin(player,
                messages.parse("<yellow><bold>Display name</bold></yellow> <gray>Enter a MiniMessage-formatted name.</gray>"),
                input -> {
                    try {
                        messages.parse(input);
                        tools.setDisplayName(toolId, input);
                        saved(player, toolId, "display name");
                    } catch (Exception exception) {
                        showError(player, exception);
                    }
                    openEditor(player, toolId);
                },
                () -> openEditor(player, toolId));
    }

    private void promptTargets(Player player, String toolId) {
        ToolDefinition tool = tools.find(toolId).orElseThrow();
        String example = tool.trackingType() == TrackingType.BLOCKS_BROKEN
                ? "stone, deepslate, cobblestone"
                : "zombie, wither_skeleton";
        prompts.begin(player,
                messages.parse("<blue><bold>Tracking targets</bold></blue> <gray>Enter comma-separated values (e.g. <white>"
                        + example + "</white>) or <white>none</white> for every target.</gray>"),
                input -> {
                    List<String> targets = input.equalsIgnoreCase("none") || input.isBlank()
                            ? List.of()
                            : java.util.Arrays.stream(input.split(",")).map(String::trim).toList();
                    change(player, toolId, "tracking targets",
                            () -> tools.setTrackingTargets(toolId, targets),
                            () -> openEditor(player, toolId));
                },
                () -> openEditor(player, toolId));
    }

    private void promptWorld(Player player, String toolId) {
        prompts.begin(player,
                messages.parse("<aqua><bold>World name</bold></aqua> <gray>Enter the exact unloaded world name to toggle.</gray>"),
                input -> {
                    if (input.isBlank() || input.contains(".")) {
                        showError(player, new IllegalArgumentException("Enter a valid world name."));
                        openWorlds(player, toolId);
                        return;
                    }
                    change(player, toolId, "allowed worlds",
                            () -> tools.toggleWorld(toolId, input.trim()),
                            () -> openWorlds(player, toolId));
                },
                () -> openWorlds(player, toolId));
    }

    private void promptRequirement(Player player, String toolId, int level) {
        prompts.begin(player,
                messages.parse("<yellow><bold>Next threshold</bold></yellow> <gray>Enter a positive whole number.</gray>"),
                input -> {
                    try {
                        long value = Long.parseLong(input.replace("_", "").replace(",", ""));
                        change(player, toolId, "level requirement",
                                () -> tools.setLevelRequirement(toolId, level, value),
                                () -> openLevelEditor(player, toolId, level));
                    } catch (NumberFormatException exception) {
                        showError(player, new IllegalArgumentException("Requirement must be a whole number."));
                        openLevelEditor(player, toolId, level);
                    }
                },
                () -> openLevelEditor(player, toolId, level));
    }

    private void promptEnchantments(Player player, String toolId, int level) {
        prompts.begin(player,
                messages.parse("<light_purple><bold>Enchantments</bold></light_purple> <gray>Use <white>efficiency=2, unbreaking=1</white>, or <white>none</white>.</gray>"),
                input -> {
                    try {
                        Map<String, Integer> enchantments = parseEnchantments(input);
                        change(player, toolId, "level enchantments",
                                () -> tools.setLevelEnchantments(toolId, level, enchantments),
                                () -> openLevelEditor(player, toolId, level));
                    } catch (IllegalArgumentException exception) {
                        showError(player, exception);
                        openLevelEditor(player, toolId, level);
                    }
                },
                () -> openLevelEditor(player, toolId, level));
    }

    private void promptLore(Player player, String toolId, int level) {
        prompts.begin(player,
                messages.parse("<aqua><bold>Level lore</bold></aqua> <gray>Enter MiniMessage lore and separate lines with <white>;;</white>. Placeholders: <white>{level}, {max_level}, {current}, {required}, {world}, {bar}</white>.</gray>"),
                input -> {
                    try {
                        List<String> lore = java.util.Arrays.stream(input.split(";;", -1))
                                .map(String::trim)
                                .filter(line -> !line.isEmpty())
                                .toList();
                        lore.forEach(messages::parse);
                        change(player, toolId, "level lore",
                                () -> tools.setLevelLore(toolId, level, lore),
                                () -> openLevelEditor(player, toolId, level));
                    } catch (RuntimeException exception) {
                        showError(player, exception);
                        openLevelEditor(player, toolId, level);
                    }
                },
                () -> openLevelEditor(player, toolId, level));
    }

    private void setBaseMaterial(Player player, String toolId, InventoryClickEvent event) {
        Material material = selectedMaterial(player, event);
        if (material == null) {
            showError(player, new IllegalArgumentException("Put an item on your cursor or in your main hand first."));
            return;
        }
        change(player, toolId, "base material", () -> tools.setBaseMaterial(toolId, material),
                () -> openEditor(player, toolId));
    }

    private void setLevelMaterial(Player player, String toolId, int level, InventoryClickEvent event) {
        if (event.isRightClick()) {
            change(player, toolId, "material upgrade", () -> tools.setLevelMaterialUpgrade(toolId, level, null),
                    () -> openLevelEditor(player, toolId, level));
            return;
        }
        Material material = selectedMaterial(player, event);
        if (material == null) {
            showError(player, new IllegalArgumentException("Put an item on your cursor or in your main hand first."));
            return;
        }
        change(player, toolId, "material upgrade", () -> tools.setLevelMaterialUpgrade(toolId, level, material),
                () -> openLevelEditor(player, toolId, level));
    }

    private void addLevel(Player player, String toolId) {
        try {
            int number = tools.addLevel(toolId);
            saved(player, toolId, "level " + number);
            openLevelEditor(player, toolId, number);
        } catch (Exception exception) {
            showError(player, exception);
            openLevels(player, toolId, 0);
        }
    }

    private void removeLevel(Player player, String toolId, int level, InventoryClickEvent event) {
        if (!event.isShiftClick() || !event.isRightClick()) {
            showError(player, new IllegalArgumentException("Shift-right-click to confirm removing the final level."));
            return;
        }
        change(player, toolId, "levels", () -> tools.removeLastLevel(toolId),
                () -> openLevels(player, toolId, 0));
    }

    private void giveSelf(Player player, String toolId) {
        ToolDefinition definition = tools.find(toolId).orElse(null);
        if (definition == null) {
            showError(player, new IllegalArgumentException("That tool no longer exists."));
            openAdminList(player, 0);
            return;
        }
        if (!grants.grant(player, definition, true)) {
            messages.send(player, "invalid-world", Map.of(
                    "tool", definition.displayName(),
                    "world", messages.plain(player.getWorld().getName())));
        }
        openEditor(player, toolId);
    }

    private void deleteTool(Player player, String toolId, InventoryClickEvent event) {
        if (!event.isShiftClick() || !event.isRightClick()) {
            messages.send(player, "delete-armed", Map.of("tool", messages.plain(toolId)));
            return;
        }
        long now = System.currentTimeMillis();
        PendingDelete pending = pendingDeletes.get(player.getUniqueId());
        if (pending == null || !pending.toolId().equals(toolId) || pending.expiresAt() < now) {
            pendingDeletes.put(player.getUniqueId(), new PendingDelete(toolId, now + 8_000L));
            messages.send(player, "delete-armed", Map.of("tool", messages.plain(toolId)));
            return;
        }
        try {
            tools.deleteTool(toolId);
            pendingDeletes.remove(player.getUniqueId());
            messages.send(player, "tool-deleted", Map.of("tool", messages.plain(toolId)));
            openAdminList(player, 0);
        } catch (Exception exception) {
            showError(player, exception);
            openEditor(player, toolId);
        }
    }

    private void change(Player player, String toolId, String field, ConfigChange action, Runnable reopen) {
        try {
            action.run();
            saved(player, toolId, field);
        } catch (Exception exception) {
            showError(player, exception);
        }
        reopen.run();
    }

    private void saved(Player player, String toolId, String field) {
        ToolDefinition tool = tools.find(toolId).orElse(null);
        messages.send(player, "editor-saved", Map.of(
                "field", messages.plain(field),
                "tool", tool == null ? messages.plain(toolId) : tool.displayName()
        ));
    }

    private void showError(Player player, Exception exception) {
        String error = exception.getMessage() == null ? "Could not apply that change." : exception.getMessage();
        messages.send(player, "editor-error", Map.of("error", messages.plain(error)));
    }

    private boolean requireAdmin(Player player) {
        if (player.hasPermission("plexontools.gui")) {
            return true;
        }
        player.closeInventory();
        messages.send(player, "no-permission");
        return false;
    }

    private ItemStack showcaseIcon(Player player, ToolDefinition tool) {
        Optional<ToolState> owned = itemService.findBestOwned(player, tool.id());
        boolean worldAllowed = tool.isAllowedWorld(player.getWorld().getName());
        boolean boundHere = owned.map(state -> state.boundWorld().equalsIgnoreCase(player.getWorld().getName())).orElse(true);
        boolean locked = !worldAllowed || !boundHere;
        Material material = locked ? Material.GRAY_DYE : tool.baseMaterial();
        List<Component> lore = new ArrayList<>();
        lore.add(messages.parse("<dark_gray>ID: " + messages.plain(tool.id()) + "</dark_gray>"));
        lore.add(Component.empty());
        lore.add(messages.parse("<gray>Tracks:</gray> <white>" + tool.trackingType().displayName() + "</white>"));
        lore.add(messages.parse("<gray>Targets:</gray> <white>" + messages.plain(summarize(tool.targetNames().stream().sorted().toList(), 4)) + "</white>"));
        lore.add(messages.parse("<gray>Worlds:</gray> <white>" + messages.plain(String.join(", ", tool.allowedWorlds())) + "</white>"));
        lore.add(Component.empty());
        if (owned.isPresent()) {
            Map<String, String> values = itemService.placeholders(tool, owned.get());
            lore.add(messages.parse("<gray>Your tool:</gray> <yellow>Level {level}/{max_level}</yellow>", values));
            lore.add(messages.parse("<gray>Progress:</gray> <white>{current}/{required}</white>", values));
            lore.add(messages.parse("{bar}", values));
        } else {
            lore.add(messages.parse("<dark_gray>No owned instance found in your inventory.</dark_gray>"));
        }
        lore.add(Component.empty());
        Optional<ToolLevel> reward = owned.isPresent()
                ? tool.nextLevel(owned.get().level())
                : Optional.of(tool.firstLevel());
        if (reward.isPresent()) {
            ToolLevel next = reward.get();
            String rewardTitle = owned.isPresent() ? "Next level rewards" : "Starting rewards";
            lore.add(messages.parse("<gold><bold>" + rewardTitle + "</bold></gold> <dark_gray>• Level " + next.number() + "</dark_gray>"));
            lore.add(messages.parse("<gray>Enchantments:</gray> <white>" + formatEnchantments(next.enchantments()) + "</white>"));
            Material rewardMaterial = next.materialUpgrade() == null ? tool.baseMaterial() : next.materialUpgrade();
            lore.add(messages.parse("<gray>Material:</gray> <white>" + rewardMaterial.name() + "</white>"));
            lore.add(Component.empty());
        } else {
            lore.add(messages.parse("<green><bold>Maximum level reached</bold></green>"));
            lore.add(Component.empty());
        }
        if (locked) {
            lore.add(messages.parse("<red><bold>Locked in this world</bold></red>"));
        } else {
            lore.add(messages.parse("<green><bold>Available here</bold></green>"));
        }
        Component name = locked
                ? messages.parse("<red><bold>Locked</bold></red> <dark_gray>•</dark_gray> " + tool.displayName())
                : messages.parse(tool.displayName());
        return ItemFactory.create(material, name, lore, !locked && owned.isPresent());
    }

    private ItemStack adminToolIcon(ToolDefinition tool) {
        return button("editor", tool.id(), tool.baseMaterial(), tool.displayName(),
                "<dark_gray>" + messages.plain(tool.id()) + "</dark_gray>", "",
                "<gray>Status:</gray> " + (tool.enabled() ? "<green>enabled</green>" : "<red>disabled</red>"),
                "<gray>Tracking:</gray> <white>" + tool.trackingType().displayName() + "</white>",
                "<gray>Worlds:</gray> <white>" + tool.allowedWorlds().size() + "</white>",
                "<gray>Levels:</gray> <white>" + tool.levels().size() + "</white>", "",
                "<yellow>Click to edit.</yellow>");
    }

    private ItemStack previewIcon(ToolDefinition tool) {
        ToolState preview = new ToolState(tool.id(), UUID.randomUUID(), 1, 0L,
                tool.allowedWorlds().iterator().next(), UUID.randomUUID());
        ItemStack item = itemService.apply(ItemStack.of(tool.baseMaterial()), tool, preview);
        tag(item, "preview", tool.id());
        return item;
    }

    private ItemStack previewIcon(ToolDefinition tool, ToolLevel level) {
        ToolState preview = new ToolState(tool.id(), UUID.randomUUID(), level.number(), 0L,
                tool.allowedWorlds().iterator().next(), UUID.randomUUID());
        ItemStack item = itemService.apply(ItemStack.of(tool.baseMaterial()), tool, preview);
        tag(item, "preview", tool.id());
        return item;
    }

    private ItemStack button(String action, String value, Material material, String name, String... loreLines) {
        List<Component> lore = java.util.Arrays.stream(loreLines)
                .map(line -> line.isEmpty() ? Component.empty() : messages.parse(line))
                .toList();
        ItemStack item = ItemFactory.create(material, messages.parse(name), lore);
        tag(item, action, value);
        return item;
    }

    private void tag(ItemStack item, String action, String value) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value == null ? "" : value);
        item.setItemMeta(meta);
    }

    private static String tag(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private void fill(Inventory inventory) {
        ItemStack filler = ItemFactory.create(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private void addNavigation(Inventory inventory, int page, int pages, String action) {
        if (page > 0) {
            inventory.setItem(inventory.getSize() - 9, button(action, Integer.toString(page - 1), Material.ARROW,
                    "<yellow><bold>Previous page</bold></yellow>", "<gray>Page " + page + " of " + pages + "</gray>"));
        }
        if (page + 1 < pages) {
            inventory.setItem(inventory.getSize() - 1, button(action, Integer.toString(page + 1), Material.ARROW,
                    "<yellow><bold>Next page</bold></yellow>", "<gray>Page " + (page + 2) + " of " + pages + "</gray>"));
        }
    }

    private static int[] contentSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int column = 1; column <= 7; column++) {
                slots.add(row * 9 + column);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int pageCount(int items, int pageSize) {
        return Math.max(1, (int) Math.ceil(items / (double) Math.max(1, pageSize)));
    }

    private static int clampPage(int page, int pages) {
        return Math.max(0, Math.min(page, pages - 1));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static String summarize(List<String> values, int maximum) {
        if (values.isEmpty()) {
            return "All";
        }
        String result = values.stream().limit(maximum).collect(Collectors.joining(", "));
        return values.size() > maximum ? result + " +" + (values.size() - maximum) : result;
    }

    private static String formatEnchantments(Map<Enchantment, Integer> enchantments) {
        if (enchantments.isEmpty()) {
            return "None";
        }
        return enchantments.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getKey().toString()))
                .map(entry -> entry.getKey().getKey().getKey().toUpperCase(Locale.ROOT) + " " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private static Map<String, Integer> parseEnchantments(String input) {
        if (input.isBlank() || input.equalsIgnoreCase("none")) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String token : input.split(",")) {
            String[] pair = token.trim().split("[:=]", 2);
            if (pair.length != 2 || pair[0].isBlank()) {
                throw new IllegalArgumentException("Use enchantment=level pairs separated by commas.");
            }
            try {
                result.put(pair[0].trim(), Integer.parseInt(pair[1].trim()));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid enchantment level in: " + token);
            }
        }
        return result;
    }

    private static Material selectedMaterial(Player player, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            return cursor.getType();
        }
        Material held = player.getInventory().getItemInMainHand().getType();
        return held.isAir() ? null : held;
    }

    @FunctionalInterface
    private interface ConfigChange {
        void run() throws Exception;
    }

    private record PendingDelete(String toolId, long expiresAt) {
    }
}
