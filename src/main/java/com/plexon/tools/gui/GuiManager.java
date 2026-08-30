package com.plexon.tools.gui;

import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.config.CategoryRepository;
import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.config.WorldMenuRepository;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.model.LevelRequirement;
import com.plexon.tools.model.RequirementMode;
import com.plexon.tools.model.AbilityTarget;
import com.plexon.tools.model.ToolAbilitySettings;
import com.plexon.tools.model.ToolAbilityType;
import com.plexon.tools.model.ToolCategory;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.model.ToolLevel;
import com.plexon.tools.model.TrackingType;
import com.plexon.tools.model.WorldToolMenu;
import com.plexon.tools.service.ChatPromptService;
import com.plexon.tools.service.ToolActivationService;
import com.plexon.tools.service.ToolGrantService;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class GuiManager implements Listener {
    private static final int[] GRID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final JavaPlugin plugin;
    private final CategoryRepository categories;
    private final ToolConfigRepository tools;
    private final WorldMenuRepository worldMenus;
    private final ToolItemService itemService;
    private final ToolActivationService activations;
    private final ToolGrantService grants;
    private final ChatPromptService prompts;
    private final PluginSettings settings;
    private final MessageService messages;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;
    private final Map<UUID, PendingDelete> pendingDeletes = new HashMap<>();
    private final Map<UUID, String> targetSearches = new HashMap<>();

    public GuiManager(
            JavaPlugin plugin,
            CategoryRepository categories,
            ToolConfigRepository tools,
            WorldMenuRepository worldMenus,
            ToolItemService itemService,
            ToolActivationService activations,
            ToolGrantService grants,
            ChatPromptService prompts,
            PluginSettings settings,
            MessageService messages
    ) {
        this.plugin = plugin;
        this.categories = categories;
        this.tools = tools;
        this.worldMenus = worldMenus;
        this.itemService = itemService;
        this.activations = activations;
        this.grants = grants;
        this.prompts = prompts;
        this.settings = settings;
        this.messages = messages;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.valueKey = new NamespacedKey(plugin, "gui_value");
    }

    public void openShowcase(Player player, int requestedPage) {
        openShowcase(player, player, null, requestedPage);
    }

    public void openPlayerEntry(Player viewer, Player subject) {
        openWorldToolMenu(viewer, subject, subject.getWorld().getName());
    }

    private void openWorldToolMenu(Player viewer, Player subject, String worldName) {
        if (subject.getWorld().getName().equalsIgnoreCase(worldName)) {
            activations.reconcile(subject);
        }
        WorldToolMenu menu = worldMenus.menuFor(worldName);
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.WORLD_TOOL_MENU,
                null, 0, 0, worldName, subject.getUniqueId(), null);
        String title = menu.title().replace("{world}", messages.plain(worldName));
        if (!viewer.equals(subject)) {
            title += " <dark_gray>• " + messages.plain(subject.getName()) + "</dark_gray>";
        }
        Inventory inventory = Bukkit.createInventory(holder, menu.size(), messages.parse(title));
        holder.attach(inventory);
        fill(inventory, menu.fillerMaterial(), menu.fillerName());

        WorldMenuLayout layout = worldMenuLayout(menu, worldName);
        int visible = 0;
        for (Map.Entry<String, Integer> entry : layout.toolSlots().entrySet()) {
            ToolDefinition definition = tools.find(entry.getKey()).orElse(null);
            if (definition == null || !definition.enabled()) {
                continue;
            }
            boolean active = activations.isActive(subject, definition, worldName);
            Integer panelSlot = layout.panelSlots().get(entry.getKey());
            inventory.setItem(entry.getValue(), worldToolCard(
                    subject, definition, worldName, active, panelSlot != null));
            if (panelSlot != null) {
                inventory.setItem(panelSlot, worldTogglePanel(
                        subject, definition, worldName, active));
            }
            visible++;
        }
        if (visible == 0) {
            String detail = settings.worldMenuAutoShowAllowedTools()
                    ? "<gray>No enabled tool currently allows this world.</gray>"
                    : "<gray>No allowed tool is pinned to this world menu.</gray>";
            inventory.setItem(menu.size() / 2, button("noop", "", Material.BARRIER,
                    "<red><bold>No tools available</bold></red>", detail,
                    "<dark_gray>World: " + messages.plain(worldName) + "</dark_gray>"));
        } else if (layout.omittedTools() > 0) {
            inventory.setItem(0, button("noop", "", Material.ORANGE_DYE,
                    "<gold><bold>Menu capacity reached</bold></gold>",
                    "<yellow>" + layout.omittedTools() + " allowed tool(s) could not fit.</yellow>",
                    "<gray>Increase the row count or rearrange pinned slots.</gray>"));
        }
        inventory.setItem(menu.size() - 1, button("close", "", Material.BARRIER,
                "<red><bold>Close</bold></red>", "<gray>Close this menu.</gray>"));
        viewer.openInventory(inventory);
    }

    public void openShowcase(
            Player viewer,
            Player subject,
            String categoryId,
            int requestedPage
    ) {
        int rows = settings.showcaseRows();
        int size = rows * 9;
        int[] slots = contentSlots(rows);
        List<ToolDefinition> visible = tools.all().stream()
                .filter(ToolDefinition::enabled)
                .filter(tool -> categoryId == null || tool.category().equalsIgnoreCase(categoryId))
                .filter(tool -> settings.showLockedTools() || tool.isAllowedWorld(subject.getWorld().getName()))
                .sorted(Comparator.comparing(ToolDefinition::id))
                .toList();
        int pages = pageCount(visible.size(), slots.length);
        int page = clampPage(requestedPage, pages);

        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.SHOWCASE,
                null, page, 0, null, subject.getUniqueId(), categoryId);
        String title = categoryId == null
                ? settings.showcaseTitle()
                : categories.find(categoryId).map(ToolCategory::displayName).orElse(settings.showcaseTitle());
        String subjectSuffix = viewer.equals(subject)
                ? ""
                : " <dark_gray>• " + messages.plain(subject.getName()) + "</dark_gray>";
        Inventory inventory = Bukkit.createInventory(holder, size,
                messages.parse(title + subjectSuffix + " <dark_gray>• "
                        + (page + 1) + "/" + pages + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);

        int offset = page * slots.length;
        for (int index = 0; index < slots.length && offset + index < visible.size(); index++) {
            ToolDefinition definition = visible.get(offset + index);
            inventory.setItem(slots[index], showcaseIcon(subject, definition));
        }
        addNavigation(inventory, page, pages, "showcase-page");
        if (categories.size() > 1) {
            inventory.setItem(size - 9, button("category-select", "", Material.CHEST,
                    "<aqua><bold>Categories</bold></aqua>",
                    "<gray>Return to category selection.</gray>"));
        }
        inventory.setItem(size - 5, button("close", "", Material.BARRIER,
                "<red><bold>Close</bold></red>", "<gray>Close this menu.</gray>"));
        viewer.openInventory(inventory);
    }

    private void openCategorySelection(Player viewer, Player subject) {
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.CATEGORY_SELECT,
                null, 0, 0, null, subject.getUniqueId(), null);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse(settings.categoryTitle()
                        + (viewer.equals(subject) ? "" : " <dark_gray>• "
                        + messages.plain(subject.getName()) + "</dark_gray>")));
        holder.attach(inventory);
        fill(inventory);
        for (ToolCategory category : categories.sorted()) {
            List<String> lore = new ArrayList<>(category.description());
            long count = tools.all().stream()
                    .filter(ToolDefinition::enabled)
                    .filter(tool -> tool.category().equalsIgnoreCase(category.id()))
                    .count();
            lore.add("");
            lore.add("<gray>Tools:</gray> <white>" + count + "</white>");
            lore.add("<yellow>Click to browse.</yellow>");
            inventory.setItem(category.slot(), button("showcase-category", category.id(), category.icon(),
                    category.displayName(), lore.toArray(String[]::new)));
        }
        inventory.setItem(49, button("showcase-all", "", Material.NETHER_STAR,
                "<gradient:#4158D0:#C850C0><bold>All Tools</bold></gradient>",
                "<gray>Browse every enabled category together.</gray>"));
        inventory.setItem(53, button("close", "", Material.BARRIER,
                "<red><bold>Close</bold></red>", "<gray>Close this menu.</gray>"));
        viewer.openInventory(inventory);
    }

    public void openAdminDashboard(Player player) {
        if (!requireAdmin(player)) {
            return;
        }
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.ADMIN_DASHBOARD,
                null, 0, 0);
        Inventory inventory = Bukkit.createInventory(holder, 36, messages.parse(settings.adminTitle()));
        holder.attach(inventory);
        fill(inventory);
        inventory.setItem(10, button("admin-list", "", Material.DIAMOND_PICKAXE,
                "<aqua><bold>Tool Manager</bold></aqua>",
                "<gray>Create and edit every tool profile.</gray>",
                "<white>" + tools.size() + " configured tool(s)</white>"));
        inventory.setItem(12, button("create", "", Material.LIME_DYE,
                "<green><bold>Create New Tool</bold></green>",
                "<gray>Uses the held item's material.</gray>"));
        inventory.setItem(14, button("world-menus", "", Material.ENDER_CHEST,
                "<gradient:#41E296:#A8FF78><bold>World Tool Menus</bold></gradient>",
                "<gray>Pin tools and customize each</gray>",
                "<gray>world's player activation GUI.</gray>",
                "<white>" + worldMenus.size() + " configured world menu(s)</white>"));
        inventory.setItem(16, button("global-settings", "", Material.COMPARATOR,
                "<yellow><bold>Global Settings</bold></yellow>",
                "<gray>World, effects, and enforcement.</gray>"));
        inventory.setItem(20, button("categories", "", Material.CHEST,
                "<light_purple><bold>Category Manager</bold></light_purple>",
                "<gray>Organize tools inside the admin editor.</gray>",
                "<white>" + categories.size() + " configured category(s)</white>"));
        inventory.setItem(22, button("showcase-entry", "", Material.COMPASS,
                "<gradient:#4158D0:#C850C0><bold>Live World Menu</bold></gradient>",
                "<gray>Preview your current world's /pt GUI.</gray>"));
        inventory.setItem(24, button("world-menu-style", "", Material.PAINTING,
                "<light_purple><bold>Player Menu Appearance</bold></light_purple>",
                "<gray>Edit default tool cards and ON/OFF panels.</gray>"));
        inventory.setItem(31, button("close", "", Material.BARRIER,
                "<red><bold>Close</bold></red>", "<gray>Close the editor.</gray>"));
        player.openInventory(inventory);
    }

    private void openWorldMenuManager(Player player, int requestedPage) {
        if (!requireAdmin(player)) {
            return;
        }
        Map<String, String> worldNames = new LinkedHashMap<>();
        Bukkit.getWorlds().stream()
                .sorted(Comparator.comparing(World::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(world -> worldNames.put(WorldToolMenu.normalize(world.getName()), world.getName()));
        worldMenus.all().stream().map(WorldToolMenu::worldName)
                .forEach(world -> worldNames.putIfAbsent(WorldToolMenu.normalize(world), world));
        List<String> worlds = worldNames.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        int pages = pageCount(worlds.size(), GRID_SLOTS.length);
        int page = clampPage(requestedPage, pages);
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.WORLD_MENUS,
                null, page, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<gradient:#41E296:#A8FF78><bold>World Tool Menus</bold></gradient>"
                        + " <dark_gray>• " + (page + 1) + "/" + pages + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);

        int offset = page * GRID_SLOTS.length;
        for (int index = 0; index < GRID_SLOTS.length && offset + index < worlds.size(); index++) {
            String worldName = worlds.get(offset + index);
            WorldToolMenu configured = worldMenus.find(worldName).orElse(null);
            long allowedTools = tools.all().stream()
                    .filter(ToolDefinition::enabled)
                    .filter(tool -> tool.isAllowedWorld(worldName))
                    .count();
            World loaded = Bukkit.getWorld(worldName);
            Material icon = loaded == null ? Material.MAP : switch (loaded.getEnvironment()) {
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> Material.GRASS_BLOCK;
            };
            inventory.setItem(GRID_SLOTS[index], button("world-menu-editor", worldName, icon,
                    "<aqua><bold>" + messages.plain(worldName) + "</bold></aqua>",
                    configured == null
                            ? "<aqua>Using default layout</aqua>"
                            : "<green>Custom layout configured</green>",
                    "<gray>Allowed tools:</gray> <white>" + allowedTools + "</white>",
                    "<gray>Pinned tool slots:</gray> <white>"
                            + (configured == null ? 0 : configured.toolSlots().size()) + "</white>", "",
                    "<yellow>Click to customize.</yellow>"));
        }
        addNavigation(inventory, page, pages, "world-menus-page");
        inventory.setItem(45, button("admin-dashboard", "", Material.ARROW,
                "<yellow><bold>Dashboard</bold></yellow>", "<gray>Return to the admin dashboard.</gray>"));
        inventory.setItem(49, button("create-world-menu", "", Material.LIME_DYE,
                "<green><bold>Add Unloaded World</bold></green>",
                "<gray>Enter an exact world name in chat.</gray>"));
        player.openInventory(inventory);
    }

    private void openWorldMenuEditor(Player player, String rawWorldName) {
        if (!requireAdmin(player)) {
            return;
        }
        String worldName = WorldToolMenu.normalize(rawWorldName);
        WorldToolMenu menu;
        try {
            menu = worldMenus.ensureWorld(worldName);
        } catch (Exception exception) {
            showError(player, exception);
            openWorldMenuManager(player, 0);
            return;
        }
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.WORLD_MENU_EDITOR,
                null, 0, 0, worldName);
        Inventory inventory = Bukkit.createInventory(holder, 36,
                messages.parse("<green><bold>World Menu</bold></green> <dark_gray>• "
                        + messages.plain(worldName) + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);
        inventory.setItem(10, button("world-menu-title", worldName, Material.NAME_TAG,
                "<yellow><bold>Menu Title</bold></yellow>", menu.title(), "",
                "<gray>Click to edit with MiniMessage.</gray>"));
        inventory.setItem(12, button("world-menu-rows", worldName, Material.CHEST,
                "<aqua><bold>Menu Size</bold></aqua>",
                "<white>" + menu.rows() + " rows</white>", "",
                "<gray>Click to cycle from 3 to 6 rows.</gray>"));
        inventory.setItem(14, button("world-menu-filler", worldName, menu.fillerMaterial(),
                "<gold><bold>Filler Material</bold></gold>",
                "<white>" + menu.fillerMaterial().name() + "</white>", "",
                "<gray>Click using your cursor or held item.</gray>"));
        inventory.setItem(16, button("world-menu-filler-name", worldName, Material.OAK_SIGN,
                "<light_purple><bold>Filler Name</bold></light_purple>",
                "<gray>Current:</gray> " + (menu.fillerName().isBlank()
                        ? "<dark_gray>blank</dark_gray>" : menu.fillerName()), "",
                "<gray>Click to edit with MiniMessage.</gray>"));
        inventory.setItem(20, button("world-menu-tools", worldName, Material.DIAMOND_PICKAXE,
                "<green><bold>Pinned Tool Slots</bold></green>",
                "<white>" + menu.toolSlots().size() + " pinned tool(s)</white>", "",
                "<gray>Allowed tools appear automatically by default.</gray>",
                "<gray>Pin tools here to choose their exact slots.</gray>"));
        inventory.setItem(22, button("world-menu-preview", worldName, Material.COMPASS,
                "<gradient:#4158D0:#C850C0><bold>Preview Menu</bold></gradient>",
                "<gray>Preview this player-facing layout.</gray>",
                player.getWorld().getName().equalsIgnoreCase(worldName)
                        ? "<green>Activation controls are live.</green>"
                        : "<yellow>Travel here to toggle tools.</yellow>"));
        inventory.setItem(24, button("world-menu-style", "", Material.PAINTING,
                "<light_purple><bold>Tool Cards & Toggle Panels</bold></light_purple>",
                "<gray>Edit the default player-facing appearance.</gray>"));
        inventory.setItem(31, button("world-menus", "", Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to all world menus.</gray>"));
        player.openInventory(inventory);
    }

    private void openWorldMenuStyle(Player player) {
        if (!requireAdmin(player)) {
            return;
        }
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.WORLD_MENU_STYLE,
                null, 0, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<light_purple><bold>Player Menu Appearance</bold></light_purple>"));
        holder.attach(inventory);
        fill(inventory);

        inventory.setItem(10, toggleButton("menu-style-toggle",
                "world-menu.auto-show-allowed-tools", Material.ENDER_EYE,
                "Auto-show Allowed Tools", settings.worldMenuAutoShowAllowedTools()));
        inventory.setItem(12, button("menu-style-material", "world-menu.tool-card.material",
                settings.worldMenuToolCard().resolveMaterial(Material.DIAMOND_PICKAXE),
                "<aqua><bold>Tool Card Material</bold></aqua>",
                "<white>" + settings.worldMenuToolCard().material() + "</white>", "",
                "<gray>Left-click using a held/cursor item.</gray>",
                "<gray>Right-click to restore dynamic <white>TOOL</white>.</gray>"));
        inventory.setItem(14, button("menu-style-name", "world-menu.tool-card.display-name",
                Material.NAME_TAG, "<yellow><bold>Tool Card Name</bold></yellow>",
                settings.worldMenuToolCard().displayName(), "",
                "<gray>Click to edit with MiniMessage.</gray>"));
        inventory.setItem(16, button("menu-style-lore", "world-menu.tool-card.lore",
                Material.WRITABLE_BOOK, "<gold><bold>Tool Card Lore</bold></gold>",
                "<white>" + settings.worldMenuToolCard().lore().size() + " line(s)</white>", "",
                "<gray>Click to edit lines using <white>;;</white>.</gray>"));
        inventory.setItem(19, toggleButton("menu-style-toggle",
                "world-menu.tool-card.glint-when-active", Material.GLOW_INK_SAC,
                "Active Tool-card Glint", settings.worldMenuToolCardActiveGlint()));
        inventory.setItem(21, toggleButton("menu-style-toggle",
                "world-menu.toggle-panel.enabled", Material.LEVER,
                "ON/OFF Panel", settings.worldMenuTogglePanelEnabled()));

        inventory.setItem(28, button("menu-style-material",
                "world-menu.toggle-panel.active.material",
                settings.worldMenuActivePanel().resolveMaterial(Material.LIME_DYE),
                "<green><bold>ON Panel Material</bold></green>",
                "<white>" + settings.worldMenuActivePanel().material() + "</white>"));
        inventory.setItem(29, button("menu-style-name",
                "world-menu.toggle-panel.active.display-name", Material.LIME_DYE,
                "<green><bold>ON Panel Name</bold></green>",
                settings.worldMenuActivePanel().displayName()));
        inventory.setItem(30, button("menu-style-lore",
                "world-menu.toggle-panel.active.lore", Material.GREEN_DYE,
                "<green><bold>ON Panel Lore</bold></green>",
                "<white>" + settings.worldMenuActivePanel().lore().size() + " line(s)</white>"));
        inventory.setItem(32, button("menu-style-material",
                "world-menu.toggle-panel.inactive.material",
                settings.worldMenuInactivePanel().resolveMaterial(Material.RED_DYE),
                "<red><bold>OFF Panel Material</bold></red>",
                "<white>" + settings.worldMenuInactivePanel().material() + "</white>"));
        inventory.setItem(33, button("menu-style-name",
                "world-menu.toggle-panel.inactive.display-name", Material.RED_DYE,
                "<red><bold>OFF Panel Name</bold></red>",
                settings.worldMenuInactivePanel().displayName()));
        inventory.setItem(34, button("menu-style-lore",
                "world-menu.toggle-panel.inactive.lore", Material.WRITABLE_BOOK,
                "<red><bold>OFF Panel Lore</bold></red>",
                "<white>" + settings.worldMenuInactivePanel().lore().size() + " line(s)</white>"));

        ToolDefinition preview = tools.all().stream()
                .filter(ToolDefinition::enabled)
                .sorted(Comparator.comparing(ToolDefinition::id))
                .findFirst().orElse(null);
        if (preview != null) {
            String worldName = player.getWorld().getName();
            boolean active = activations.isActive(player, preview, worldName);
            ItemStack card = worldToolCard(player, preview, worldName, active, true);
            ItemStack panel = worldTogglePanel(player, preview, worldName, active);
            tag(card, "noop", "");
            tag(panel, "noop", "");
            inventory.setItem(40, card);
            inventory.setItem(49, panel);
        }
        inventory.setItem(45, button("admin-dashboard", "", Material.ARROW,
                "<yellow><bold>Dashboard</bold></yellow>",
                "<gray>Return to the administrative dashboard.</gray>"));
        inventory.setItem(53, button("close", "", Material.BARRIER,
                "<red><bold>Close</bold></red>", "<gray>Close this editor.</gray>"));
        player.openInventory(inventory);
    }

    private void openWorldMenuTools(Player player, String worldName, int requestedPage) {
        WorldToolMenu menu = worldMenus.find(worldName).orElse(null);
        if (menu == null || !requireAdmin(player)) {
            openWorldMenuManager(player, 0);
            return;
        }
        List<ToolDefinition> definitions = tools.all().stream()
                .sorted(Comparator.comparing(ToolDefinition::id))
                .toList();
        int pages = pageCount(definitions.size(), GRID_SLOTS.length);
        int page = clampPage(requestedPage, pages);
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.WORLD_MENU_TOOLS,
                null, page, 0, worldName);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<green><bold>Pinned Tool Slots</bold></green> <dark_gray>• "
                        + messages.plain(worldName) + " • " + (page + 1) + "/" + pages + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);
        int offset = page * GRID_SLOTS.length;
        for (int index = 0; index < GRID_SLOTS.length && offset + index < definitions.size(); index++) {
            ToolDefinition definition = definitions.get(offset + index);
            boolean assigned = menu.contains(definition.id());
            boolean allowed = definition.isAllowedWorld(worldName);
            int slot = menu.slot(definition.id()).orElse(-1);
            inventory.setItem(GRID_SLOTS[index], button("world-menu-tool", definition.id(),
                    allowed ? definition.firstLevel().material() : Material.GRAY_DYE,
                    (assigned ? "<green>✓ </green>" : "") + definition.displayName(),
                    "<dark_gray>" + messages.plain(definition.id()) + "</dark_gray>",
                    "<gray>Allowed in world:</gray> " + (allowed ? "<green>yes</green>" : "<red>no</red>"),
                    assigned ? "<gray>Menu slot:</gray> <white>" + slot + "</white>"
                            : (allowed && settings.worldMenuAutoShowAllowedTools()
                                    ? "<aqua>Automatic layout</aqua>"
                                    : "<dark_gray>Not displayed</dark_gray>"), "",
                    allowed ? "<yellow>Left-click to " + (assigned ? "unpin" : "pin") + ".</yellow>"
                            : "<red>Add this world in the tool editor first.</red>",
                    assigned ? "<aqua>Right-click to change its slot.</aqua>" : ""));
        }
        addNavigation(inventory, page, pages, "world-menu-tools-page");
        inventory.setItem(45, button("world-menu-editor", worldName, Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to the world menu editor.</gray>"));
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
        inventory.setItem(45, button("admin-dashboard", "", Material.ARROW,
                "<yellow><bold>Dashboard</bold></yellow>", "<gray>Return to the admin dashboard.</gray>"));
        inventory.setItem(46, button("showcase-entry", "", Material.COMPASS,
                "<aqua><bold>Player showcase</bold></aqua>", "<gray>Preview the player-facing menu.</gray>"));
        inventory.setItem(49, button("create", "", Material.LIME_DYE,
                "<green><bold>Create a tool</bold></green>",
                "<gray>Uses your held item's material,</gray>", "<gray>or an iron pickaxe if your hand is empty.</gray>"));
        inventory.setItem(53, button("close", "", Material.BARRIER,
                "<red><bold>Close</bold></red>", "<gray>Close this menu.</gray>"));
        player.openInventory(inventory);
    }

    private void openCategoryManager(Player player, int requestedPage) {
        if (!requireAdmin(player)) {
            return;
        }
        List<ToolCategory> values = categories.sorted();
        int pages = pageCount(values.size(), GRID_SLOTS.length);
        int page = clampPage(requestedPage, pages);
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.CATEGORIES,
                null, page, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<light_purple><bold>Category Manager</bold></light_purple>"
                        + " <dark_gray>• " + (page + 1) + "/" + pages + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);
        int offset = page * GRID_SLOTS.length;
        for (int index = 0; index < GRID_SLOTS.length && offset + index < values.size(); index++) {
            ToolCategory category = values.get(offset + index);
            long assigned = tools.all().stream()
                    .filter(tool -> tool.category().equalsIgnoreCase(category.id()))
                    .count();
            inventory.setItem(GRID_SLOTS[index], button("category-editor", category.id(), category.icon(),
                    category.displayName(),
                    "<dark_gray>" + messages.plain(category.id()) + "</dark_gray>", "",
                    "<gray>Showcase slot:</gray> <white>" + category.slot() + "</white>",
                    "<gray>Assigned tools:</gray> <white>" + assigned + "</white>", "",
                    "<yellow>Click to customize.</yellow>"));
        }
        addNavigation(inventory, page, pages, "categories-page");
        inventory.setItem(45, button("admin-dashboard", "", Material.ARROW,
                "<yellow><bold>Dashboard</bold></yellow>", "<gray>Return to the admin dashboard.</gray>"));
        inventory.setItem(49, button("create-category", "", Material.LIME_DYE,
                "<green><bold>Create Category</bold></green>",
                "<gray>Enter a new category ID in chat.</gray>"));
        player.openInventory(inventory);
    }

    private void openCategoryEditor(Player player, String categoryId) {
        ToolCategory category = categories.find(categoryId).orElse(null);
        if (category == null || !requireAdmin(player)) {
            openCategoryManager(player, 0);
            return;
        }
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.CATEGORY_EDITOR,
                null, 0, 0, category.id());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                messages.parse("<light_purple><bold>Edit Category</bold></light_purple> <dark_gray>•</dark_gray> "
                        + category.displayName()));
        holder.attach(inventory);
        fill(inventory);
        inventory.setItem(10, button("category-name", category.id(), Material.NAME_TAG,
                "<yellow><bold>Display Name</bold></yellow>", category.displayName(), "",
                "<gray>Click to edit with MiniMessage.</gray>"));
        inventory.setItem(12, button("category-icon", category.id(), category.icon(),
                "<gold><bold>Category Icon</bold></gold>",
                "<white>" + category.icon().name() + "</white>", "",
                "<gray>Click using your cursor or held item.</gray>"));
        inventory.setItem(14, button("category-slot", category.id(), Material.HOPPER,
                "<aqua><bold>Showcase Slot</bold></aqua>",
                "<white>" + category.slot() + "</white>", "",
                "<gray>Click to enter a slot from 0 to 53.</gray>"));
        inventory.setItem(16, button("category-description", category.id(), Material.WRITABLE_BOOK,
                "<green><bold>Description</bold></green>",
                "<white>" + category.description().size() + " line(s)</white>", "",
                "<gray>Separate MiniMessage lines with <white>;;</white>.</gray>"));
        inventory.setItem(22, button("categories", "", Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to all categories.</gray>"));
        player.openInventory(inventory);
    }

    private void openCategoryAssignment(Player player, String toolId) {
        ToolDefinition tool = tools.find(toolId).orElse(null);
        if (tool == null || !requireAdmin(player)) {
            openAdminList(player, 0);
            return;
        }
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.CATEGORY_ASSIGN,
                tool.id(), 0, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<light_purple><bold>Assign Category</bold></light_purple> <dark_gray>•</dark_gray> "
                        + tool.displayName()));
        holder.attach(inventory);
        fill(inventory);
        List<ToolCategory> values = categories.sorted();
        for (int index = 0; index < GRID_SLOTS.length && index < values.size(); index++) {
            ToolCategory category = values.get(index);
            boolean selected = tool.category().equalsIgnoreCase(category.id());
            inventory.setItem(GRID_SLOTS[index], button("assign-category", category.id(), category.icon(),
                    (selected ? "<green>✓ </green>" : "") + category.displayName(),
                    selected ? "<green>Currently assigned.</green>" : "<gray>Click to assign.</gray>"));
        }
        inventory.setItem(45, button("editor", tool.id(), Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to the tool editor.</gray>"));
        player.openInventory(inventory);
    }

    private void openGlobalSettings(Player player) {
        if (!requireAdmin(player)) {
            return;
        }
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.GLOBAL_SETTINGS,
                null, 0, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                messages.parse("<yellow><bold>Global Settings</bold></yellow>"));
        holder.attach(inventory);
        fill(inventory);
        inventory.setItem(10, toggleButton("global-toggle", "settings.enforce-bound-world",
                Material.ENDER_EYE, "Enforce Bound World", settings.enforceBoundWorld()));
        inventory.setItem(12, button("noop", "", Material.PLAYER_HEAD,
                "<green><bold>Player Binding Enforced</bold></green>",
                "<green>Always enabled in PlexonTools 3.5.</green>", "",
                "<gray>Bound tools cannot be transferred or used</gray>",
                "<gray>by another player, including administrators.</gray>"));
        inventory.setItem(14, toggleButton("global-toggle", "effects.level-up-particles",
                Material.FIREWORK_STAR, "Level-up Particles", settings.levelUpParticles()));
        inventory.setItem(16, button("global-sound", "", Material.NOTE_BLOCK,
                "<aqua><bold>Level-up Sound</bold></aqua>",
                "<white>" + messages.plain(settings.levelUpSound()) + "</white>", "",
                "<gray>Click to enter a Bukkit sound key.</gray>"));
        inventory.setItem(18, toggleButton("global-toggle", "effects.progress-action-bar",
                Material.EXPERIENCE_BOTTLE, "Progress Action Bar",
                settings.progressActionBar()));
        inventory.setItem(22, button("admin-dashboard", "", Material.ARROW,
                "<yellow><bold>Dashboard</bold></yellow>", "<gray>Return to the admin dashboard.</gray>"));
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
        ToolLevel firstLevel = tool.firstLevel();
        inventory.setItem(15, button("requirements", tool.id(), Material.WRITABLE_BOOK,
                "<blue><bold>Requirement engine</bold></blue>",
                "<gray>Level 1 mode:</gray> <white>" + firstLevel.requirement().mode().displayName() + "</white>",
                "<gray>Configured total:</gray> <white>" + firstLevel.requirement().requiredTotal() + "</white>",
                tool.hasMixedRequirementModes()
                        ? "<light_purple>Levels use mixed modes.</light_purple>"
                        : "<dark_gray>All levels currently use one mode.</dark_gray>", "",
                "<gray>Click to edit level 1. Each level can</gray>",
                "<gray>use its own mode, targets, and amounts.</gray>"));
        inventory.setItem(16, button("levels", tool.id(), Material.EXPERIENCE_BOTTLE,
                "<green><bold>Progression levels</bold></green>",
                "<white>" + tool.levels().size() + " configured level(s)</white>", "", "<gray>Click to edit rewards and thresholds.</gray>"));
        ToolCategory category = categories.find(tool.category()).orElse(null);
        inventory.setItem(22, button("category-assign", tool.id(),
                category == null ? Material.CHEST : category.icon(),
                "<light_purple><bold>Category</bold></light_purple>",
                category == null ? "<red>Unknown: " + messages.plain(tool.category()) + "</red>"
                        : category.displayName(), "",
                "<gray>Click to assign or reassign this tool.</gray>"));
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
                    allowed
                            ? "<gray>Allowed and visible in <white>/pt</white> by default.</gray>"
                            : "<gray>Locked and hidden from this world's menu.</gray>",
                    allowed ? "<yellow>Click to remove access.</yellow>"
                            : "<green>Click to allow access.</green>"));
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
            inventory.setItem(GRID_SLOTS[index], button("edit-level", Integer.toString(level.number()), level.material(),
                    "<gradient:#41E296:#A8FF78><bold>Level " + level.number() + "</bold></gradient>",
                    level.displayName(),
                    "<dark_gray>" + (level.displayNameOverride() ? "Custom name" : "Inherited name") + "</dark_gray>", "",
                    "<gray>Progress starts at:</gray> <white>0</white> <dark_gray>(resets per level)</dark_gray>",
                    "<gray>Requirement:</gray> <white>" + requirementSummary(level.requirement()) + "</white>",
                    "<gray>Enchantments:</gray> <white>" + formatEnchantments(level.enchantments()) + "</white>",
                    "<gray>Material:</gray> <white>" + level.material().name() + "</white>",
                    "", "<yellow>Click to edit the complete profile.</yellow>",
                    "<dark_gray>Shift-click inside the editor to manage order.</dark_gray>"));
        }
        addNavigation(inventory, page, pages, "levels-page");
        inventory.setItem(45, button("editor", tool.id(), Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to the tool editor.</gray>"));
        inventory.setItem(49, button("add-level", tool.id(), Material.LIME_DYE,
                "<green><bold>Add level</bold></green>",
                "<gray>Clones the final profile and doubles</gray>", "<gray>its next threshold.</gray>"));
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

        inventory.setItem(10, button("level-name", tool.id(), Material.NAME_TAG,
                "<yellow><bold>Level display name</bold></yellow>", level.displayName(),
                "<dark_gray>" + (level.displayNameOverride() ? "Custom at this level" : "Inherited from an earlier profile") + "</dark_gray>", "",
                "<gray>Left-click to edit.</gray>", "<gray>Right-click to inherit.</gray>"));
        inventory.setItem(11, button("level-material", tool.id(), level.material(),
                "<gold><bold>Level material</bold></gold>",
                "<white>" + level.material().name() + "</white>",
                "<dark_gray>" + (level.materialOverride() ? "Changes at this level" : "Inherited from an earlier profile") + "</dark_gray>", "",
                "<gray>Left-click with an item to set.</gray>", "<gray>Right-click to inherit.</gray>"));
        inventory.setItem(12, button("level-requirement", tool.id(), Material.CLOCK,
                "<yellow><bold>Requirement engine</bold></yellow>",
                "<gray>Mode:</gray> <white>" + level.requirement().mode().displayName() + "</white>",
                "<gray>Requirement:</gray> <white>" + requirementSummary(level.requirement()) + "</white>",
                "<gray>Level boundary:</gray> <green>progress resets to zero</green>", "",
                "<gray>Configure mode, exact amounts, step</gray>",
                "<gray>controls, target search, and quotas.</gray>"));
        inventory.setItem(13, button("level-enchantments", tool.id(), Material.ENCHANTED_BOOK,
                "<light_purple><bold>Enchantments</bold></light_purple>",
                "<white>" + formatEnchantments(level.enchantments()) + "</white>", "",
                "<gray>Open the visual enchantment editor.</gray>"));
        inventory.setItem(14, button("level-lore", tool.id(), Material.BOOK,
                "<aqua><bold>Lore</bold></aqua>", "<white>" + level.lore().size() + " line(s)</white>", "",
                "<gray>Edit, move, add, or delete individual lines.</gray>"));
        inventory.setItem(15, button("noop", tool.id(), Material.OBSIDIAN,
                "<green><bold>Unbreakable</bold></green>",
                "<green>Always enabled for bound tools.</green>", "",
                "<dark_gray>This protection is enforced by PlexonTools 3.5.</dark_gray>"));
        inventory.setItem(16, button("level-glint", tool.id(), Material.GLOW_INK_SAC,
                "<light_purple><bold>Enchantment glint</bold></light_purple>",
                "<white>" + level.glint().displayName() + "</white>", "",
                "<gray>Click to cycle automatic, on, and off.</gray>"));
        inventory.setItem(19, button("noop", tool.id(), Material.BOOKSHELF,
                "<green><bold>Enchantments Hidden</bold></green>",
                "<green>Always enabled for a clean tooltip.</green>"));
        inventory.setItem(20, button("noop", tool.id(), Material.IRON_CHESTPLATE,
                "<green><bold>Vanilla Details Hidden</bold></green>",
                "<green>Attributes and additional tooltip data are hidden.</green>"));
        inventory.setItem(21, button("level-model-data", tool.id(), Material.PAINTING,
                "<aqua><bold>Custom model data</bold></aqua>",
                "<white>" + (level.customModelData() == null ? "Not set" : level.customModelData()) + "</white>", "",
                "<gray>Left-click to set an integer.</gray>", "<gray>Right-click to clear.</gray>"));
        inventory.setItem(22, button("level-abilities", tool.id(), Material.BLAZE_POWDER,
                "<gradient:#FF9A8B:#FF6A88><bold>Tool Abilities</bold></gradient>",
                "<white>" + level.abilities().size() + " enabled ability/abilities</white>", "",
                "<gray>Configure Auto Smelt, 3×3 mining,</gray>",
                "<gray>EXP boost, potion effects, and Magnet.</gray>"));
        inventory.setItem(23, button("duplicate-level", tool.id(), Material.SLIME_BALL,
                "<green><bold>Duplicate after this level</bold></green>",
                "<gray>Copies this entire profile and shifts</gray>", "<gray>later levels forward.</gray>"));
        if (level.number() > 1) {
            inventory.setItem(24, button("move-level-up", tool.id(), Material.SPECTRAL_ARROW,
                    "<yellow><bold>Move one level earlier</bold></yellow>",
                    "<gray>Swaps this complete profile with level " + (level.number() - 1) + ".</gray>"));
        }
        if (level.number() < tool.maxLevel()) {
            inventory.setItem(25, button("move-level-down", tool.id(), Material.ARROW,
                    "<yellow><bold>Move one level later</bold></yellow>",
                    "<gray>Swaps this complete profile with level " + (level.number() + 1) + ".</gray>"));
        }
        inventory.setItem(31, previewIcon(tool, level));
        inventory.setItem(45, button("levels", tool.id(), Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to all levels.</gray>"));
        if (level.number() > 1) {
            inventory.setItem(47, button("edit-level", Integer.toString(level.number() - 1), Material.ARROW,
                    "<yellow><bold>Previous level</bold></yellow>", "<gray>Open level " + (level.number() - 1) + ".</gray>"));
        }
        if (level.number() < tool.maxLevel()) {
            inventory.setItem(51, button("edit-level", Integer.toString(level.number() + 1), Material.ARROW,
                    "<yellow><bold>Next level</bold></yellow>", "<gray>Open level " + (level.number() + 1) + ".</gray>"));
        }
        if (tool.levels().size() > 1) {
            inventory.setItem(53, button("remove-level", tool.id(), Material.RED_DYE,
                    "<red><bold>Delete this level</bold></red>",
                    "<gray>Shift-right-click to delete level " + level.number() + "</gray>",
                    "<dark_red>and renumber every later level.</dark_red>"));
        }
        player.openInventory(inventory);
    }

    private void openAbilities(Player player, String toolId, int levelNumber) {
        ToolDefinition tool = tools.find(toolId).orElse(null);
        ToolLevel level = tool == null ? null : tool.level(levelNumber).orElse(null);
        if (tool == null || level == null || !requireAdmin(player)) {
            openAdminList(player, 0);
            return;
        }
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.ABILITIES,
                tool.id(), 0, level.number());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<gradient:#FF9A8B:#FF6A88><bold>Abilities</bold></gradient>"
                        + " <dark_gray>• Level " + level.number() + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);
        int[] slots = {11, 13, 15, 29, 33};
        ToolAbilityType[] types = ToolAbilityType.values();
        for (int index = 0; index < types.length; index++) {
            ToolAbilityType type = types[index];
            ToolAbilitySettings ability = level.abilities().get(type);
            List<String> lore = new ArrayList<>();
            lore.add(type.description());
            lore.add("");
            lore.add(ability == null ? "<red>Disabled</red>" : "<green>Enabled</green>");
            if (ability != null && type == ToolAbilityType.EXP_BOOSTER) {
                lore.add("<gray>Multiplier:</gray> <white>" + ability.multiplier() + "×</white>");
            }
            if (ability != null && type == ToolAbilityType.MOB_POTION_EFFECT) {
                lore.add("<gray>Effect:</gray> <white>" + messages.plain(ability.potionEffect())
                        + " " + ability.potionLevel() + "</white>");
                lore.add("<gray>Duration:</gray> <white>" + ability.durationTicks() + " ticks</white>");
                lore.add("<gray>Target:</gray> <white>" + ability.potionTarget().name() + "</white>");
            }
            lore.add("");
            lore.add("<yellow>Left-click to toggle.</yellow>");
            if (ability != null && (type == ToolAbilityType.EXP_BOOSTER
                    || type == ToolAbilityType.MOB_POTION_EFFECT)) {
                lore.add("<aqua>Right-click to configure.</aqua>");
            }
            inventory.setItem(slots[index], button("ability-edit", type.name(),
                    abilityMaterial(type),
                    (ability == null ? "<red>" : "<green>") + "<bold>"
                            + type.displayName() + "</bold>"
                            + (ability == null ? "</red>" : "</green>"),
                    lore.toArray(String[]::new)));
        }
        inventory.setItem(45, button("edit-level", Integer.toString(level.number()), Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to the level profile.</gray>"));
        player.openInventory(inventory);
    }

    private void openRequirement(Player player, String toolId, int levelNumber) {
        ToolDefinition tool = tools.find(toolId).orElse(null);
        ToolLevel level = tool == null ? null : tool.level(levelNumber).orElse(null);
        if (tool == null || level == null || !requireAdmin(player)) {
            openAdminList(player, 0);
            return;
        }
        LevelRequirement requirement = level.requirement();
        PlexonGuiHolder holder = new PlexonGuiHolder(
                PlexonGuiHolder.View.REQUIREMENT, tool.id(), 0, level.number());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<yellow><bold>Requirement</bold></yellow> <dark_gray>• Level "
                        + level.number() + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);

        Material modeMaterial = requirement.mode() == RequirementMode.GENERAL
                ? Material.COMPASS : Material.TARGET;
        inventory.setItem(10, button("requirement-mode", tool.id(), modeMaterial,
                "<light_purple><bold>" + requirement.mode().displayName() + "</bold></light_purple>",
                requirement.mode() == RequirementMode.GENERAL
                        ? "<gray>One total across every accepted event.</gray>"
                        : "<gray>Every configured target has its own quota.</gray>", "",
                "<yellow>Click to switch modes.</yellow>",
                "<dark_gray>Mode changes reset the requirement shape,</dark_gray>",
                "<dark_gray>but existing issued items remain readable.</dark_gray>"));

        List<String> summary = new ArrayList<>();
        summary.add("<gold><bold>Current requirement</bold></gold>");
        summary.add("<gray>Mode:</gray> <white>" + requirement.mode().name() + "</white>");
        summary.add("<gray>Total:</gray> <white>" + requirement.requiredTotal() + "</white>");
        if (requirement.mode() == RequirementMode.SPECIFIC) {
            summary.add("<gray>Targets:</gray> <white>" + requirement.targets().size() + "</white>");
            requirement.targets().entrySet().stream().limit(8).forEach(entry ->
                    summary.add("<dark_gray>•</dark_gray> <white>" + humanizeKey(entry.getKey())
                            + "</white> <gray>×</gray> <yellow>" + entry.getValue() + "</yellow>"));
            if (requirement.targets().size() > 8) {
                summary.add("<dark_gray>+" + (requirement.targets().size() - 8) + " more</dark_gray>");
            }
        }
        inventory.setItem(13, button(
                requirement.mode() == RequirementMode.GENERAL ? "requirement-exact" : "noop",
                tool.id(), Material.CLOCK, summary.getFirst(),
                summary.stream().skip(1).toArray(String[]::new)));

        if (requirement.mode() == RequirementMode.GENERAL) {
            addAmountControls(inventory, "requirement-adjust", requirement.amount());
            inventory.setItem(49, button("requirement-exact", tool.id(), Material.ANVIL,
                    "<aqua><bold>Enter exact amount</bold></aqua>",
                    "<gray>Current:</gray> <white>" + requirement.amount() + "</white>", "",
                    "<gray>Click and type any positive whole number.</gray>"));
        } else {
            inventory.setItem(22, button("requirement-targets", tool.id(), Material.CHEST,
                    "<green><bold>Manage target quotas</bold></green>",
                    "<gray>Selected:</gray> <white>" + requirement.targets().size() + "</white>",
                    "<gray>Combined requirement:</gray> <white>" + requirement.requiredTotal() + "</white>", "",
                    "<gray>Browse, search, add, remove, and</gray>",
                    "<gray>set an exact amount for each target.</gray>"));
            if (requirement.targets().isEmpty()) {
                inventory.setItem(31, button("requirement-targets", tool.id(), Material.REDSTONE_TORCH,
                        "<red><bold>No targets configured</bold></red>",
                        "<gray>This level cannot progress until</gray>",
                        "<gray>at least one target is added.</gray>"));
            }
        }
        inventory.setItem(45, button("edit-level", Integer.toString(level.number()), Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to the level profile.</gray>"));
        player.openInventory(inventory);
    }

    private void openTargetSelector(Player player, String toolId, int levelNumber, int requestedPage) {
        ToolDefinition tool = tools.find(toolId).orElse(null);
        ToolLevel level = tool == null ? null : tool.level(levelNumber).orElse(null);
        if (tool == null || level == null || !requireAdmin(player)) {
            openAdminList(player, 0);
            return;
        }
        if (level.requirement().mode() != RequirementMode.SPECIFIC) {
            openRequirement(player, toolId, levelNumber);
            return;
        }
        String search = targetSearches.getOrDefault(player.getUniqueId(), "");
        List<String> options = tools.targetOptions(tool.trackingType()).stream()
                .filter(target -> search.isBlank() || target.contains(search))
                .toList();
        int pages = pageCount(options.size(), GRID_SLOTS.length);
        int page = clampPage(requestedPage, pages);
        PlexonGuiHolder holder = new PlexonGuiHolder(
                PlexonGuiHolder.View.TARGET_SELECTOR, tool.id(), page, level.number());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<green><bold>Target quotas</bold></green> <dark_gray>• Level "
                        + level.number() + " • " + (page + 1) + "/" + pages + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);

        int offset = page * GRID_SLOTS.length;
        for (int index = 0; index < GRID_SLOTS.length && offset + index < options.size(); index++) {
            String target = options.get(offset + index);
            Long amount = level.requirement().targets().get(target);
            boolean selected = amount != null;
            inventory.setItem(GRID_SLOTS[index], button("select-target", target,
                    targetIcon(tool.trackingType(), target),
                    selected
                            ? "<green><bold>✓ " + humanizeKey(target) + "</bold></green>"
                            : "<gray><bold>" + humanizeKey(target) + "</bold></gray>",
                    "<dark_gray>" + target + "</dark_gray>",
                    selected
                            ? "<gray>Required:</gray> <yellow>" + amount + "</yellow>"
                            : "<dark_gray>Not selected</dark_gray>", "",
                    selected
                            ? "<yellow>Left-click:</yellow> <gray>edit amount</gray>"
                            : "<green>Left-click:</green> <gray>add with 100 required</gray>",
                    selected ? "<red>Right-click:</red> <gray>remove</gray>" : ""));
        }
        addNavigation(inventory, page, pages, "target-page");
        inventory.setItem(45, button("requirement", tool.id(), Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to the requirement editor.</gray>"));
        inventory.setItem(48, button("target-clear-search", tool.id(), Material.MILK_BUCKET,
                "<white><bold>Clear search</bold></white>",
                search.isBlank() ? "<dark_gray>No filter is active.</dark_gray>"
                        : "<gray>Current:</gray> <white>" + messages.plain(search) + "</white>"));
        inventory.setItem(49, button("target-search", tool.id(), Material.SPYGLASS,
                "<aqua><bold>Search targets</bold></aqua>",
                search.isBlank() ? "<gray>Showing every valid target.</gray>"
                        : "<gray>Filter:</gray> <white>" + messages.plain(search) + "</white>", "",
                "<gray>Click and enter part of a target name.</gray>"));
        player.openInventory(inventory);
    }

    private void openTargetAmount(Player player, String toolId, int levelNumber, String target) {
        ToolDefinition tool = tools.find(toolId).orElse(null);
        ToolLevel level = tool == null ? null : tool.level(levelNumber).orElse(null);
        String normalized = LevelRequirement.normalize(target);
        Long amount = level == null ? null : level.requirement().targets().get(normalized);
        if (tool == null || level == null || amount == null || !requireAdmin(player)) {
            openTargetSelector(player, toolId, levelNumber, 0);
            return;
        }
        PlexonGuiHolder holder = new PlexonGuiHolder(
                PlexonGuiHolder.View.TARGET_AMOUNT, tool.id(), 0, level.number(), normalized);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<aqua><bold>Target amount</bold></aqua> <dark_gray>• "
                        + humanizeKey(normalized) + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);
        inventory.setItem(13, button("target-exact", normalized,
                targetIcon(tool.trackingType(), normalized),
                "<aqua><bold>" + humanizeKey(normalized) + "</bold></aqua>",
                "<gray>Required:</gray> <white>" + amount + "</white>", "",
                "<gray>Click to enter an exact amount in chat.</gray>"));
        addAmountControls(inventory, "target-adjust", amount);
        inventory.setItem(45, button("requirement-targets", tool.id(), Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to all target quotas.</gray>"));
        inventory.setItem(49, button("target-exact", normalized, Material.ANVIL,
                "<aqua><bold>Enter exact amount</bold></aqua>",
                "<gray>Current:</gray> <white>" + amount + "</white>"));
        inventory.setItem(53, button("target-remove", normalized, Material.RED_DYE,
                "<red><bold>Remove target</bold></red>",
                "<gray>Shift-right-click to remove this quota.</gray>"));
        player.openInventory(inventory);
    }

    private void addAmountControls(Inventory inventory, String action, long current) {
        long[] steps = {1L, 10L, 100L, 1000L};
        Material[] addMaterials = {
                Material.LIME_DYE, Material.EMERALD, Material.EMERALD_BLOCK, Material.BEACON
        };
        Material[] subtractMaterials = {
                Material.RED_DYE, Material.REDSTONE, Material.REDSTONE_BLOCK, Material.TNT
        };
        for (int index = 0; index < steps.length; index++) {
            long step = steps[index];
            inventory.setItem(19 + index, button(action, Long.toString(step), addMaterials[index],
                    "<green><bold>+" + step + "</bold></green>",
                    "<gray>Current:</gray> <white>" + current + "</white>"));
            inventory.setItem(28 + index, button(action, Long.toString(-step), subtractMaterials[index],
                    "<red><bold>-" + step + "</bold></red>",
                    "<gray>Minimum:</gray> <white>1</white>"));
        }
    }

    private void openEnchantments(Player player, String toolId, int levelNumber, int requestedPage) {
        ToolDefinition tool = tools.find(toolId).orElse(null);
        ToolLevel level = tool == null ? null : tool.level(levelNumber).orElse(null);
        if (tool == null || level == null || !requireAdmin(player)) {
            openAdminList(player, 0);
            return;
        }
        List<Enchantment> options = tools.enchantmentOptions();
        int pages = pageCount(options.size(), GRID_SLOTS.length);
        int page = clampPage(requestedPage, pages);
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.ENCHANTMENTS, tool.id(), page, level.number());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<light_purple><bold>Enchantments</bold></light_purple> <dark_gray>• Level " + level.number() + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);

        int offset = page * GRID_SLOTS.length;
        for (int index = 0; index < GRID_SLOTS.length && offset + index < options.size(); index++) {
            Enchantment enchantment = options.get(offset + index);
            int current = level.enchantments().getOrDefault(enchantment, 0);
            String key = enchantment.getKey().toString();
            String color = current > 0 ? "light_purple" : "gray";
            inventory.setItem(GRID_SLOTS[index], button("adjust-enchantment", key,
                    current > 0 ? Material.ENCHANTED_BOOK : Material.BOOK,
                    "<" + color + "><bold>" + humanizeKey(key) + "</bold></" + color + ">",
                    "<dark_gray>" + key + "</dark_gray>",
                    "<gray>Current level:</gray> <white>" + current + "</white>", "",
                    "<green>Left-click:</green> <gray>+1</gray>",
                    "<green>Shift-left:</green> <gray>+5</gray>",
                    "<red>Right-click:</red> <gray>-1</gray>",
                    "<red>Shift-right:</red> <gray>remove</gray>"));
        }
        addNavigation(inventory, page, pages, "enchantments-page");
        inventory.setItem(45, button("edit-level", Integer.toString(level.number()), Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to the level profile.</gray>"));
        inventory.setItem(49, button("enchantments-bulk", tool.id(), Material.WRITABLE_BOOK,
                "<aqua><bold>Bulk text editor</bold></aqua>",
                "<gray>Paste enchantment=level pairs in chat.</gray>"));
        inventory.setItem(53, button("clear-enchantments", tool.id(), Material.RED_DYE,
                "<red><bold>Clear all enchantments</bold></red>",
                "<gray>Shift-right-click to confirm.</gray>"));
        player.openInventory(inventory);
    }

    private void openLore(Player player, String toolId, int levelNumber, int requestedPage) {
        ToolDefinition tool = tools.find(toolId).orElse(null);
        ToolLevel level = tool == null ? null : tool.level(levelNumber).orElse(null);
        if (tool == null || level == null || !requireAdmin(player)) {
            openAdminList(player, 0);
            return;
        }
        int pages = pageCount(level.lore().size(), GRID_SLOTS.length);
        int page = clampPage(requestedPage, pages);
        PlexonGuiHolder holder = new PlexonGuiHolder(PlexonGuiHolder.View.LORE, tool.id(), page, level.number());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                messages.parse("<aqua><bold>Lore editor</bold></aqua> <dark_gray>• Level " + level.number() + "</dark_gray>"));
        holder.attach(inventory);
        fill(inventory);

        int offset = page * GRID_SLOTS.length;
        for (int index = 0; index < GRID_SLOTS.length && offset + index < level.lore().size(); index++) {
            int lineIndex = offset + index;
            String line = level.lore().get(lineIndex);
            inventory.setItem(GRID_SLOTS[index], button("lore-line", Integer.toString(lineIndex), Material.PAPER,
                    "<aqua><bold>Line " + (lineIndex + 1) + "</bold></aqua>", line, "",
                    "<yellow>Left-click:</yellow> <gray>edit</gray>",
                    "<red>Right-click:</red> <gray>delete</gray>",
                    "<yellow>Shift-left:</yellow> <gray>move up</gray>",
                    "<yellow>Shift-right:</yellow> <gray>move down</gray>"));
        }
        addNavigation(inventory, page, pages, "lore-page");
        inventory.setItem(45, button("edit-level", Integer.toString(level.number()), Material.ARROW,
                "<yellow><bold>Back</bold></yellow>", "<gray>Return to the level profile.</gray>"));
        inventory.setItem(48, button("lore-bulk", tool.id(), Material.WRITABLE_BOOK,
                "<aqua><bold>Bulk text editor</bold></aqua>",
                "<gray>Replace all lines using <white>;;</white> separators.</gray>"));
        inventory.setItem(49, button("add-lore-line", tool.id(), Material.LIME_DYE,
                "<green><bold>Add lore line</bold></green>",
                "<gray>Append one MiniMessage-formatted line.</gray>"));
        inventory.setItem(53, button("clear-lore", tool.id(), Material.RED_DYE,
                "<red><bold>Clear all lore</bold></red>",
                "<gray>Shift-right-click to confirm.</gray>"));
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PlexonGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
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
        if (holder.view() != PlexonGuiHolder.View.WORLD_TOOL_MENU
                && holder.view() != PlexonGuiHolder.View.SHOWCASE
                && holder.view() != PlexonGuiHolder.View.CATEGORY_SELECT
                && !requireAdmin(player)) {
            return;
        }

        switch (action) {
            case "close" -> player.closeInventory();
            case "showcase", "showcase-entry" -> openPlayerEntry(player, player);
            case "toggle-world-tool" -> toggleWorldTool(player, holder, value);
            case "showcase-page" -> {
                Player subject = showcaseSubject(holder, player);
                if (subject != null) {
                    openNextTick(player, () -> openShowcase(
                            player, subject, holder.categoryId(), parseInt(value, 0)));
                }
            }
            case "showcase-category" -> {
                Player subject = showcaseSubject(holder, player);
                if (subject != null) {
                    openShowcase(player, subject, value, 0);
                }
            }
            case "showcase-all" -> {
                Player subject = showcaseSubject(holder, player);
                if (subject != null) {
                    openShowcase(player, subject, null, 0);
                }
            }
            case "category-select" -> {
                Player subject = showcaseSubject(holder, player);
                if (subject != null) {
                    openCategorySelection(player, subject);
                }
            }
            case "admin-dashboard" -> openAdminDashboard(player);
            case "admin-list" -> openAdminList(player, 0);
            case "admin-page" -> openNextTick(player,
                    () -> openAdminList(player, parseInt(value, 0)));
            case "world-menus" -> openWorldMenuManager(player, 0);
            case "world-menus-page" -> openNextTick(player,
                    () -> openWorldMenuManager(player, parseInt(value, 0)));
            case "world-menu-editor" -> openWorldMenuEditor(player, value);
            case "create-world-menu" -> promptCreateWorldMenu(player);
            case "world-menu-title" -> promptWorldMenuTitle(player, value);
            case "world-menu-rows" -> cycleWorldMenuRows(player, value);
            case "world-menu-filler" -> setWorldMenuFiller(player, value, event);
            case "world-menu-filler-name" -> promptWorldMenuFillerName(player, value);
            case "world-menu-tools" -> openWorldMenuTools(player, value, 0);
            case "world-menu-tools-page" -> openNextTick(player,
                    () -> openWorldMenuTools(player, holder.context(), parseInt(value, 0)));
            case "world-menu-tool" -> editWorldMenuTool(player, holder.context(), value, event);
            case "world-menu-preview" -> openWorldToolMenu(player, player, value);
            case "world-menu-style" -> openWorldMenuStyle(player);
            case "menu-style-toggle" -> toggleWorldMenuStyle(player, value);
            case "menu-style-material" -> setWorldMenuStyleMaterial(player, value, event);
            case "menu-style-name" -> promptWorldMenuStyleName(player, value);
            case "menu-style-lore" -> promptWorldMenuStyleLore(player, value);
            case "categories" -> openCategoryManager(player, 0);
            case "categories-page" -> openNextTick(player,
                    () -> openCategoryManager(player, parseInt(value, 0)));
            case "category-editor" -> openCategoryEditor(player, value);
            case "create-category" -> createCategory(player);
            case "category-name" -> promptCategoryName(player, value);
            case "category-icon" -> setCategoryIcon(player, value, event);
            case "category-slot" -> promptCategorySlot(player, value);
            case "category-description" -> promptCategoryDescription(player, value);
            case "category-assign" -> openCategoryAssignment(player, value);
            case "assign-category" -> assignCategory(player, holder.toolId(), value);
            case "global-settings" -> openGlobalSettings(player);
            case "global-toggle" -> toggleGlobalSetting(player, value);
            case "global-sound" -> promptGlobalSound(player);
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
            case "requirements" -> openRequirement(player, value, 1);
            case "levels" -> openLevels(player, value, 0);
            case "levels-page" -> openNextTick(player,
                    () -> openLevels(player, holder.toolId(), parseInt(value, 0)));
            case "give-self" -> giveSelf(player, value);
            case "delete" -> deleteTool(player, value, event);
            case "toggle-world" -> change(player, holder.toolId(), "allowed worlds",
                    () -> tools.toggleWorld(holder.toolId(), value),
                    () -> openWorlds(player, holder.toolId()));
            case "add-world" -> promptWorld(player, value);
            case "edit-level" -> openLevelEditor(player, holder.toolId(), parseInt(value, 1));
            case "add-level" -> addLevel(player, value);
            case "level-name" -> editLevelName(player, holder.toolId(), holder.level(), event);
            case "level-requirement", "requirement" ->
                    openRequirement(player, holder.toolId(), holder.level());
            case "requirement-mode" -> toggleRequirementMode(
                    player, holder.toolId(), holder.level());
            case "requirement-adjust" -> adjustGeneralRequirement(
                    player, holder.toolId(), holder.level(), parseLong(value, 0L));
            case "requirement-exact" -> promptRequirement(
                    player, holder.toolId(), holder.level(), false, null);
            case "requirement-targets" -> openTargetSelector(
                    player, holder.toolId(), holder.level(), 0);
            case "target-page" -> openNextTick(player, () -> openTargetSelector(
                    player, holder.toolId(), holder.level(), parseInt(value, 0)));
            case "target-search" -> promptTargetSearch(
                    player, holder.toolId(), holder.level());
            case "target-clear-search" -> {
                targetSearches.remove(player.getUniqueId());
                openTargetSelector(player, holder.toolId(), holder.level(), 0);
            }
            case "select-target" -> selectTarget(
                    player, holder.toolId(), holder.level(), value, event);
            case "target-adjust" -> adjustTargetRequirement(
                    player, holder.toolId(), holder.level(), holder.context(), parseLong(value, 0L));
            case "target-exact" -> promptRequirement(
                    player, holder.toolId(), holder.level(), true,
                    holder.context() == null ? value : holder.context());
            case "target-remove" -> removeTarget(
                    player, holder.toolId(), holder.level(), holder.context(), event);
            case "level-enchantments" -> openEnchantments(player, holder.toolId(), holder.level(), 0);
            case "level-lore" -> openLore(player, holder.toolId(), holder.level(), 0);
            case "level-material" -> setLevelMaterial(player, holder.toolId(), holder.level(), event);
            case "level-unbreakable" -> toggleUnbreakable(player, holder.toolId(), holder.level());
            case "level-glint" -> cycleGlint(player, holder.toolId(), holder.level());
            case "level-hide-enchants" -> toggleHideEnchantments(player, holder.toolId(), holder.level());
            case "level-hide-attributes" -> toggleHideAttributes(player, holder.toolId(), holder.level());
            case "level-model-data" -> editCustomModelData(player, holder.toolId(), holder.level(), event);
            case "level-abilities" -> openAbilities(player, holder.toolId(), holder.level());
            case "ability-edit" -> editAbility(
                    player, holder.toolId(), holder.level(), ToolAbilityType.parse(value), event);
            case "duplicate-level" -> duplicateLevel(player, holder.toolId(), holder.level());
            case "move-level-up" -> moveLevel(player, holder.toolId(), holder.level(), -1);
            case "move-level-down" -> moveLevel(player, holder.toolId(), holder.level(), 1);
            case "remove-level" -> removeLevel(player, holder.toolId(), holder.level(), event);
            case "enchantments-page" -> openNextTick(player, () -> openEnchantments(
                    player, holder.toolId(), holder.level(), parseInt(value, 0)));
            case "adjust-enchantment" -> adjustEnchantment(player, holder.toolId(), holder.level(), value, event);
            case "enchantments-bulk" -> promptEnchantments(player, holder.toolId(), holder.level());
            case "clear-enchantments" -> clearEnchantments(player, holder.toolId(), holder.level(), event);
            case "lore-page" -> openNextTick(player, () -> openLore(
                    player, holder.toolId(), holder.level(), parseInt(value, 0)));
            case "lore-line" -> editLoreLine(player, holder.toolId(), holder.level(), parseInt(value, 0), event);
            case "add-lore-line" -> promptLoreLine(player, holder.toolId(), holder.level(), -1);
            case "lore-bulk" -> promptLore(player, holder.toolId(), holder.level());
            case "clear-lore" -> clearLore(player, holder.toolId(), holder.level(), event);
            default -> {
                // Non-interactive showcase and preview items intentionally do nothing.
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PlexonGuiHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingDeletes.remove(playerId);
        targetSearches.remove(playerId);
    }

    private Player showcaseSubject(PlexonGuiHolder holder, Player fallback) {
        if (holder.subjectId() == null || holder.subjectId().equals(fallback.getUniqueId())) {
            return fallback;
        }
        Player subject = Bukkit.getPlayer(holder.subjectId());
        if (subject == null) {
            showError(fallback, new IllegalArgumentException("The target player is no longer online."));
            fallback.closeInventory();
        }
        return subject;
    }

    private void toggleWorldTool(Player viewer, PlexonGuiHolder holder, String toolId) {
        Player subject = showcaseSubject(holder, viewer);
        if (subject == null) {
            return;
        }
        String worldName = holder.context() == null
                ? subject.getWorld().getName() : holder.context();
        ToolDefinition definition = tools.find(toolId).orElse(null);
        if (definition == null || !activations.isAvailable(definition, worldName)
                || !subject.getWorld().getName().equalsIgnoreCase(worldName)) {
            messages.send(viewer, "activation-unavailable");
            openWorldToolMenu(viewer, subject, worldName);
            return;
        }
        ToolActivationService.ToggleResult result = activations.toggle(subject, definition, worldName);
        switch (result) {
            case ACTIVATED -> messages.send(subject, "tool-activated", Map.of(
                    "tool", definition.displayName(), "world", messages.plain(worldName)));
            case DEACTIVATED -> messages.send(subject, "tool-deactivated", Map.of(
                    "tool", definition.displayName(), "world", messages.plain(worldName)));
            case INVENTORY_FULL -> messages.send(subject, "activation-inventory-full");
            case UNAVAILABLE -> messages.send(subject, "activation-unavailable");
        }
        openWorldToolMenu(viewer, subject, worldName);
    }

    private void promptCreateWorldMenu(Player player) {
        prompts.begin(player,
                messages.parse("<green><bold>World menu name</bold></green>"
                        + " <gray>Enter the exact world name.</gray>"),
                input -> {
                    try {
                        WorldToolMenu menu = worldMenus.ensureWorld(input);
                        messages.send(player, "editor-saved", Map.of(
                                "field", "world menu", "tool", messages.plain(menu.worldName())));
                        openWorldMenuEditor(player, menu.worldName());
                    } catch (Exception exception) {
                        showError(player, exception);
                        openWorldMenuManager(player, 0);
                    }
                },
                () -> openWorldMenuManager(player, 0));
    }

    private void promptWorldMenuTitle(Player player, String worldName) {
        prompts.begin(player,
                messages.parse("<yellow><bold>World menu title</bold></yellow>"
                        + " <gray>Enter a MiniMessage title. <white>{world}</white> is supported.</gray>"),
                input -> {
                    try {
                        messages.parse(input.replace("{world}", messages.plain(worldName)));
                        worldMenus.setTitle(worldName, input);
                        messages.send(player, "editor-saved", Map.of(
                                "field", "world menu title", "tool", messages.plain(worldName)));
                    } catch (Exception exception) {
                        showError(player, exception);
                    }
                    openWorldMenuEditor(player, worldName);
                },
                () -> openWorldMenuEditor(player, worldName));
    }

    private void cycleWorldMenuRows(Player player, String worldName) {
        try {
            WorldToolMenu menu = worldMenus.find(worldName).orElseThrow();
            int rows = menu.rows() == 6 ? 3 : menu.rows() + 1;
            worldMenus.setRows(worldName, rows);
            messages.send(player, "editor-saved", Map.of(
                    "field", "world menu size", "tool", messages.plain(worldName)));
        } catch (Exception exception) {
            showError(player, exception);
        }
        openWorldMenuEditor(player, worldName);
    }

    private void setWorldMenuFiller(
            Player player,
            String worldName,
            InventoryClickEvent event
    ) {
        Material material = selectedMaterial(player, event);
        try {
            if (material == null) {
                throw new IllegalArgumentException(
                        "Place an item on your cursor or hold one in your main hand.");
            }
            worldMenus.setFillerMaterial(worldName, material);
            messages.send(player, "editor-saved", Map.of(
                    "field", "world menu filler", "tool", messages.plain(worldName)));
        } catch (Exception exception) {
            showError(player, exception);
        }
        openWorldMenuEditor(player, worldName);
    }

    private void promptWorldMenuFillerName(Player player, String worldName) {
        prompts.begin(player,
                messages.parse("<light_purple><bold>Filler name</bold></light_purple>"
                        + " <gray>Enter MiniMessage text or <white>none</white> for a blank name.</gray>"),
                input -> {
                    try {
                        String name = input.equalsIgnoreCase("none") ? " " : input;
                        messages.parse(name);
                        worldMenus.setFillerName(worldName, name);
                        messages.send(player, "editor-saved", Map.of(
                                "field", "world menu filler name", "tool", messages.plain(worldName)));
                    } catch (Exception exception) {
                        showError(player, exception);
                    }
                    openWorldMenuEditor(player, worldName);
                },
                () -> openWorldMenuEditor(player, worldName));
    }

    private void toggleWorldMenuStyle(Player player, String path) {
        if (!Set.of(
                "world-menu.auto-show-allowed-tools",
                "world-menu.tool-card.glint-when-active",
                "world-menu.toggle-panel.enabled"
        ).contains(path)) {
            showError(player, new IllegalArgumentException("Unknown menu appearance toggle."));
            openWorldMenuStyle(player);
            return;
        }
        updateWorldMenuSetting(player, path,
                !plugin.getConfig().getBoolean(path), "menu appearance toggle");
    }

    private void setWorldMenuStyleMaterial(
            Player player,
            String path,
            InventoryClickEvent event
    ) {
        if (!Set.of(
                "world-menu.tool-card.material",
                "world-menu.toggle-panel.active.material",
                "world-menu.toggle-panel.inactive.material"
        ).contains(path)) {
            showError(player, new IllegalArgumentException("Unknown menu material setting."));
            openWorldMenuStyle(player);
            return;
        }
        if (path.equals("world-menu.tool-card.material") && event.isRightClick()) {
            updateWorldMenuSetting(player, path, "TOOL", "tool-card material");
            return;
        }
        Material material = selectedMaterial(player, event);
        if (material == null) {
            showError(player, new IllegalArgumentException(
                    "Place an item on your cursor or hold one in your main hand."));
            openWorldMenuStyle(player);
            return;
        }
        updateWorldMenuSetting(player, path, material.name(), "menu material");
    }

    private void promptWorldMenuStyleName(Player player, String path) {
        if (!Set.of(
                "world-menu.tool-card.display-name",
                "world-menu.toggle-panel.active.display-name",
                "world-menu.toggle-panel.inactive.display-name"
        ).contains(path)) {
            showError(player, new IllegalArgumentException("Unknown menu display-name setting."));
            openWorldMenuStyle(player);
            return;
        }
        prompts.begin(player,
                messages.parse("<yellow><bold>Menu display name</bold></yellow>"
                        + " <gray>Enter MiniMessage text. Tool-menu placeholders are supported.</gray>"),
                input -> {
                    try {
                        if (input.isBlank()) {
                            throw new IllegalArgumentException("Display name cannot be blank.");
                        }
                        messages.parse(input);
                        updateWorldMenuSetting(player, path, input, "menu display name");
                    } catch (Exception exception) {
                        showError(player, exception);
                        openWorldMenuStyle(player);
                    }
                },
                () -> openWorldMenuStyle(player));
    }

    private void promptWorldMenuStyleLore(Player player, String path) {
        if (!Set.of(
                "world-menu.tool-card.lore",
                "world-menu.toggle-panel.active.lore",
                "world-menu.toggle-panel.inactive.lore"
        ).contains(path)) {
            showError(player, new IllegalArgumentException("Unknown menu lore setting."));
            openWorldMenuStyle(player);
            return;
        }
        prompts.begin(player,
                messages.parse("<gold><bold>Menu lore</bold></gold>"
                        + " <gray>Enter MiniMessage lines separated by <white>;;</white>,"
                        + " or <white>none</white>.</gray>"),
                input -> {
                    try {
                        List<String> lines = input.equalsIgnoreCase("none")
                                ? List.of()
                                : java.util.Arrays.stream(input.split(";;", -1))
                                        .map(String::trim)
                                        .toList();
                        lines.stream().filter(line -> !line.isEmpty()).forEach(messages::parse);
                        updateWorldMenuSetting(player, path, lines, "menu lore");
                    } catch (Exception exception) {
                        showError(player, exception);
                        openWorldMenuStyle(player);
                    }
                },
                () -> openWorldMenuStyle(player));
    }

    private void updateWorldMenuSetting(
            Player player,
            String path,
            Object value,
            String field
    ) {
        Object previous = plugin.getConfig().get(path);
        try {
            plugin.getConfig().set(path, value);
            settings.load(plugin.getConfig());
            plugin.saveConfig();
            if (path.equals("world-menu.auto-show-allowed-tools")) {
                Bukkit.getOnlinePlayers().forEach(activations::reconcile);
            }
            messages.send(player, "editor-saved", Map.of(
                    "field", messages.plain(field),
                    "tool", "<light_purple>player menu</light_purple>"));
        } catch (RuntimeException exception) {
            plugin.getConfig().set(path, previous);
            settings.load(plugin.getConfig());
            showError(player, exception);
        }
        openWorldMenuStyle(player);
    }

    private void editWorldMenuTool(
            Player player,
            String worldName,
            String toolId,
            InventoryClickEvent event
    ) {
        ToolDefinition definition = tools.find(toolId).orElse(null);
        WorldToolMenu menu = worldMenus.find(worldName).orElse(null);
        if (definition == null || menu == null) {
            openWorldMenuManager(player, 0);
            return;
        }
        if (event.isRightClick() && menu.contains(toolId)) {
            promptWorldMenuToolSlot(player, worldName, toolId);
            return;
        }
        if (!menu.contains(toolId) && !definition.isAllowedWorld(worldName)) {
            showError(player, new IllegalArgumentException(
                    "Allow this world in the tool editor before pinning the tool."));
            openWorldMenuTools(player, worldName, 0);
            return;
        }
        try {
            boolean added = worldMenus.toggleTool(worldName, toolId);
            messages.send(player, "editor-saved", Map.of(
                    "field", added ? "pinned world-tool slot" : "automatic world-tool slot",
                    "tool", definition.displayName()));
            Bukkit.getOnlinePlayers().forEach(activations::reconcile);
        } catch (Exception exception) {
            showError(player, exception);
        }
        openWorldMenuTools(player, worldName, 0);
    }

    private void promptWorldMenuToolSlot(Player player, String worldName, String toolId) {
        WorldToolMenu menu = worldMenus.find(worldName).orElseThrow();
        prompts.begin(player,
                messages.parse("<aqua><bold>World menu slot</bold></aqua>"
                        + " <gray>Enter an unused inner slot for this <white>"
                        + menu.rows() + "-row</white> menu. Leave room beneath it"
                        + " for the ON/OFF panel.</gray>"),
                input -> {
                    try {
                        worldMenus.setToolSlot(worldName, toolId, Integer.parseInt(input.trim()));
                        messages.send(player, "editor-saved", Map.of(
                                "field", "world menu tool slot",
                                "tool", tools.find(toolId).map(ToolDefinition::displayName)
                                        .orElse(messages.plain(toolId))));
                    } catch (Exception exception) {
                        showError(player, exception);
                    }
                    openWorldMenuTools(player, worldName, 0);
                },
                () -> openWorldMenuTools(player, worldName, 0));
    }

    private void createCategory(Player player) {
        prompts.begin(player,
                messages.parse("<light_purple><bold>New category ID</bold></light_purple>"
                        + " <gray>Use lowercase letters, numbers, <white>_</white>, or <white>-</white>.</gray>"),
                input -> {
                    try {
                        ToolCategory created = categories.create(input);
                        messages.send(player, "editor-saved", Map.of(
                                "field", "new category",
                                "tool", created.displayName()));
                        openCategoryEditor(player, created.id());
                    } catch (Exception exception) {
                        showError(player, exception);
                        openCategoryManager(player, 0);
                    }
                },
                () -> openCategoryManager(player, 0));
    }

    private void promptCategoryName(Player player, String categoryId) {
        prompts.begin(player,
                messages.parse("<yellow><bold>Category display name</bold></yellow>"
                        + " <gray>Enter a MiniMessage-formatted name.</gray>"),
                input -> {
                    try {
                        messages.parse(input);
                        categories.setDisplayName(categoryId, input);
                        messages.send(player, "editor-saved", Map.of(
                                "field", "category display name",
                                "tool", categories.find(categoryId)
                                        .map(ToolCategory::displayName)
                                        .orElse(messages.plain(categoryId))));
                    } catch (Exception exception) {
                        showError(player, exception);
                    }
                    openCategoryEditor(player, categoryId);
                },
                () -> openCategoryEditor(player, categoryId));
    }

    private void setCategoryIcon(
            Player player,
            String categoryId,
            InventoryClickEvent event
    ) {
        Material material = selectedMaterial(player, event);
        if (material == null) {
            showError(player, new IllegalArgumentException(
                    "Place an item on your cursor or hold one in your main hand."));
            openCategoryEditor(player, categoryId);
            return;
        }
        try {
            categories.setIcon(categoryId, material);
            messages.send(player, "editor-saved", Map.of(
                    "field", "category icon",
                    "tool", categories.find(categoryId)
                            .map(ToolCategory::displayName)
                            .orElse(messages.plain(categoryId))));
        } catch (Exception exception) {
            showError(player, exception);
        }
        openCategoryEditor(player, categoryId);
    }

    private void promptCategorySlot(Player player, String categoryId) {
        prompts.begin(player,
                messages.parse("<aqua><bold>Category slot</bold></aqua>"
                        + " <gray>Enter a unique inventory slot from <white>0</white> to <white>53</white>.</gray>"),
                input -> {
                    try {
                        categories.setSlot(categoryId, Integer.parseInt(input.trim()));
                        messages.send(player, "editor-saved", Map.of(
                                "field", "category slot",
                                "tool", categories.find(categoryId)
                                        .map(ToolCategory::displayName)
                                        .orElse(messages.plain(categoryId))));
                    } catch (Exception exception) {
                        showError(player, exception);
                    }
                    openCategoryEditor(player, categoryId);
                },
                () -> openCategoryEditor(player, categoryId));
    }

    private void promptCategoryDescription(Player player, String categoryId) {
        prompts.begin(player,
                messages.parse("<green><bold>Category description</bold></green>"
                        + " <gray>Enter MiniMessage lines separated with <white>;;</white>, or <white>none</white>.</gray>"),
                input -> {
                    try {
                        List<String> lines = input.equalsIgnoreCase("none")
                                ? List.of()
                                : java.util.Arrays.stream(input.split(";;", -1))
                                        .map(String::trim)
                                        .filter(line -> !line.isEmpty())
                                        .toList();
                        lines.forEach(messages::parse);
                        categories.setDescription(categoryId, lines);
                        messages.send(player, "editor-saved", Map.of(
                                "field", "category description",
                                "tool", categories.find(categoryId)
                                        .map(ToolCategory::displayName)
                                        .orElse(messages.plain(categoryId))));
                    } catch (Exception exception) {
                        showError(player, exception);
                    }
                    openCategoryEditor(player, categoryId);
                },
                () -> openCategoryEditor(player, categoryId));
    }

    private void assignCategory(Player player, String toolId, String categoryId) {
        change(player, toolId, "category",
                () -> tools.setCategory(toolId, categoryId),
                () -> openEditor(player, toolId));
    }

    private void toggleGlobalSetting(Player player, String path) {
        if (!List.of(
                "settings.enforce-bound-world",
                "effects.level-up-particles",
                "effects.progress-action-bar"
        ).contains(path)) {
            showError(player, new IllegalArgumentException("Unknown global setting."));
            openGlobalSettings(player);
            return;
        }
        try {
            plugin.getConfig().set(path, !plugin.getConfig().getBoolean(path));
            plugin.saveConfig();
            settings.load(plugin.getConfig());
            messages.send(player, "editor-saved", Map.of(
                    "field", messages.plain(path),
                    "tool", "<yellow>global settings</yellow>"));
        } catch (RuntimeException exception) {
            showError(player, exception);
        }
        openGlobalSettings(player);
    }

    private void promptGlobalSound(Player player) {
        prompts.begin(player,
                messages.parse("<aqua><bold>Level-up sound</bold></aqua>"
                        + " <gray>Enter a Bukkit sound such as <white>ENTITY_PLAYER_LEVELUP</white>"
                        + " or a namespaced sound key.</gray>"),
                input -> {
                    try {
                        String configured = input.trim();
                        if (configured.isEmpty()) {
                            throw new IllegalArgumentException("Sound cannot be blank.");
                        }
                        String normalized = configured.toLowerCase(Locale.ROOT);
                        String namespaced = normalized.contains(":")
                                ? normalized
                                : "minecraft:" + normalized.replace('_', '.');
                        NamespacedKey key = NamespacedKey.fromString(namespaced);
                        Sound sound = key == null ? null
                                : RegistryAccess.registryAccess()
                                        .getRegistry(RegistryKey.SOUND_EVENT).get(key);
                        if (sound == null) {
                            throw new IllegalArgumentException("Unknown sound: " + configured);
                        }
                        plugin.getConfig().set("effects.level-up-sound", configured);
                        plugin.saveConfig();
                        settings.load(plugin.getConfig());
                        messages.send(player, "editor-saved", Map.of(
                                "field", "level-up sound",
                                "tool", "<yellow>global settings</yellow>"));
                    } catch (Exception exception) {
                        showError(player, exception);
                    }
                    openGlobalSettings(player);
                },
                () -> openGlobalSettings(player));
    }

    private void editAbility(
            Player player,
            String toolId,
            int level,
            ToolAbilityType type,
            InventoryClickEvent event
    ) {
        ToolDefinition tool = tools.find(toolId).orElse(null);
        ToolLevel profile = tool == null ? null : tool.level(level).orElse(null);
        if (profile == null) {
            openAdminList(player, 0);
            return;
        }
        ToolAbilitySettings current = profile.abilities().get(type);
        if (!event.isRightClick()) {
            change(player, toolId, type.displayName(),
                    () -> tools.setLevelAbilityEnabled(toolId, level, type, current == null),
                    () -> openAbilities(player, toolId, level));
            return;
        }
        if (current == null) {
            change(player, toolId, type.displayName(),
                    () -> tools.setLevelAbilityEnabled(toolId, level, type, true),
                    () -> openAbilities(player, toolId, level));
            return;
        }
        if (type == ToolAbilityType.EXP_BOOSTER) {
            prompts.begin(player,
                    messages.parse("<gold><bold>EXP multiplier</bold></gold>"
                            + " <gray>Enter a value from <white>1.0</white> to <white>100.0</white>.</gray>"),
                    input -> {
                        try {
                            tools.setLevelAbilityMultiplier(toolId, level, type,
                                    Double.parseDouble(input.trim()));
                            saved(player, toolId, "EXP multiplier");
                        } catch (Exception exception) {
                            showError(player, exception);
                        }
                        openAbilities(player, toolId, level);
                    },
                    () -> openAbilities(player, toolId, level));
            return;
        }
        if (type == ToolAbilityType.MOB_POTION_EFFECT) {
            prompts.begin(player,
                    messages.parse("<light_purple><bold>Potion ability</bold></light_purple>"
                            + " <gray>Enter <white>effect,level,duration_ticks,target</white>."
                            + " Example: <white>haste,2,100,HOLDER</white>.</gray>"),
                    input -> {
                        try {
                            String[] values = input.split(",", -1);
                            if (values.length != 4) {
                                throw new IllegalArgumentException(
                                        "Use effect,level,duration_ticks,target.");
                            }
                            tools.setLevelPotionAbility(toolId, level, values[0].trim(),
                                    Integer.parseInt(values[1].trim()),
                                    Integer.parseInt(values[2].trim()),
                                    AbilityTarget.parse(values[3]));
                            saved(player, toolId, "potion ability");
                        } catch (Exception exception) {
                            showError(player, exception);
                        }
                        openAbilities(player, toolId, level);
                    },
                    () -> openAbilities(player, toolId, level));
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
        String example = switch (tool.trackingType().targetKind()) {
            case BLOCK -> "stone, deepslate, cobblestone";
            case ENTITY -> "zombie, wither_skeleton, player";
            case CROP -> "wheat, carrots, nether_wart";
            case FISH -> "cod, salmon, pufferfish";
        };
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

    private void editLevelName(Player player, String toolId, int level, InventoryClickEvent event) {
        if (event.isRightClick()) {
            change(player, toolId, "level display name inheritance",
                    () -> tools.setLevelDisplayName(toolId, level, null),
                    () -> openLevelEditor(player, toolId, level));
            return;
        }
        prompts.begin(player,
                messages.parse("<yellow><bold>Level display name</bold></yellow> <gray>Enter a MiniMessage name. All lore placeholders are supported.</gray>"),
                input -> {
                    try {
                        messages.parse(input);
                        tools.setLevelDisplayName(toolId, level, input);
                        saved(player, toolId, "level display name");
                    } catch (Exception exception) {
                        showError(player, exception);
                    }
                    openLevelEditor(player, toolId, level);
                },
                () -> openLevelEditor(player, toolId, level));
    }

    private void toggleRequirementMode(Player player, String toolId, int level) {
        ToolLevel current = tools.find(toolId).orElseThrow().level(level).orElseThrow();
        RequirementMode next = current.requirement().mode().next();
        change(player, toolId, "level requirement mode",
                () -> tools.setLevelRequirementMode(toolId, level, next),
                () -> openRequirement(player, toolId, level));
    }

    private void adjustGeneralRequirement(
            Player player,
            String toolId,
            int level,
            long delta
    ) {
        ToolLevel current = tools.find(toolId).orElseThrow().level(level).orElseThrow();
        if (current.requirement().mode() != RequirementMode.GENERAL) {
            openRequirement(player, toolId, level);
            return;
        }
        long updated = adjustAmount(current.requirement().amount(), delta);
        change(player, toolId, "level requirement",
                () -> tools.setLevelRequirementAmount(toolId, level, updated),
                () -> openRequirement(player, toolId, level));
    }

    private void promptRequirement(
            Player player,
            String toolId,
            int level,
            boolean targetAmount,
            String target
    ) {
        String label = targetAmount ? humanizeKey(LevelRequirement.normalize(target)) : "level total";
        prompts.begin(player,
                messages.parse("<yellow><bold>Exact requirement</bold></yellow> <gray>Enter a positive whole number for <white>"
                        + messages.plain(label) + "</white>.</gray>"),
                input -> {
                    try {
                        long value = Long.parseLong(input.replace("_", "").replace(",", ""));
                        if (value < 1L) {
                            throw new IllegalArgumentException("Requirement must be at least 1.");
                        }
                        if (targetAmount) {
                            change(player, toolId, "target requirement",
                                    () -> tools.setLevelTargetRequirement(toolId, level, target, value),
                                    () -> openTargetAmount(player, toolId, level, target));
                        } else {
                            change(player, toolId, "level requirement",
                                    () -> tools.setLevelRequirementAmount(toolId, level, value),
                                    () -> openRequirement(player, toolId, level));
                        }
                    } catch (NumberFormatException exception) {
                        showError(player, new IllegalArgumentException("Requirement must be a whole number."));
                        if (targetAmount) {
                            openTargetAmount(player, toolId, level, target);
                        } else {
                            openRequirement(player, toolId, level);
                        }
                    } catch (IllegalArgumentException exception) {
                        showError(player, exception);
                        if (targetAmount) {
                            openTargetAmount(player, toolId, level, target);
                        } else {
                            openRequirement(player, toolId, level);
                        }
                    }
                },
                () -> {
                    if (targetAmount) {
                        openTargetAmount(player, toolId, level, target);
                    } else {
                        openRequirement(player, toolId, level);
                    }
                });
    }

    private void promptTargetSearch(Player player, String toolId, int level) {
        prompts.begin(player,
                messages.parse("<aqua><bold>Target search</bold></aqua> <gray>Enter part of a material or mob name, or <white>clear</white>.</gray>"),
                input -> {
                    String search = LevelRequirement.normalize(input).replace(' ', '_');
                    if (search.isBlank() || search.equals("CLEAR") || search.equals("NONE")) {
                        targetSearches.remove(player.getUniqueId());
                    } else {
                        targetSearches.put(player.getUniqueId(), search);
                    }
                    openTargetSelector(player, toolId, level, 0);
                },
                () -> openTargetSelector(player, toolId, level, 0));
    }

    private void selectTarget(
            Player player,
            String toolId,
            int level,
            String target,
            InventoryClickEvent event
    ) {
        ToolLevel current = tools.find(toolId).orElseThrow().level(level).orElseThrow();
        String normalized = LevelRequirement.normalize(target);
        boolean selected = current.requirement().targets().containsKey(normalized);
        int page = event.getView().getTopInventory().getHolder() instanceof PlexonGuiHolder gui
                ? gui.page() : 0;
        if (selected && event.isRightClick()) {
            change(player, toolId, "target requirement",
                    () -> tools.setLevelTargetRequirement(toolId, level, normalized, null),
                    () -> openTargetSelector(player, toolId, level, page));
            return;
        }
        if (selected) {
            openTargetAmount(player, toolId, level, normalized);
            return;
        }
        change(player, toolId, "target requirement",
                () -> tools.setLevelTargetRequirement(toolId, level, normalized, 100L),
                () -> openTargetAmount(player, toolId, level, normalized));
    }

    private void adjustTargetRequirement(
            Player player,
            String toolId,
            int level,
            String target,
            long delta
    ) {
        String normalized = LevelRequirement.normalize(target);
        ToolLevel current = tools.find(toolId).orElseThrow().level(level).orElseThrow();
        Long amount = current.requirement().targets().get(normalized);
        if (amount == null) {
            openTargetSelector(player, toolId, level, 0);
            return;
        }
        long updated = adjustAmount(amount, delta);
        change(player, toolId, "target requirement",
                () -> tools.setLevelTargetRequirement(toolId, level, normalized, updated),
                () -> openTargetAmount(player, toolId, level, normalized));
    }

    private void removeTarget(
            Player player,
            String toolId,
            int level,
            String target,
            InventoryClickEvent event
    ) {
        if (!event.isShiftClick() || !event.isRightClick()) {
            showError(player, new IllegalArgumentException("Shift-right-click to remove this target."));
            return;
        }
        String normalized = LevelRequirement.normalize(target);
        change(player, toolId, "target requirement",
                () -> tools.setLevelTargetRequirement(toolId, level, normalized, null),
                () -> openTargetSelector(player, toolId, level, 0));
    }

    private void promptEnchantments(Player player, String toolId, int level) {
        prompts.begin(player,
                messages.parse("<light_purple><bold>Enchantments</bold></light_purple> <gray>Use <white>efficiency=2, unbreaking=1</white>, or <white>none</white>.</gray>"),
                input -> {
                    try {
                        Map<String, Integer> enchantments = parseEnchantments(input);
                        change(player, toolId, "level enchantments",
                                () -> tools.setLevelEnchantments(toolId, level, enchantments),
                                () -> openEnchantments(player, toolId, level, 0));
                    } catch (IllegalArgumentException exception) {
                        showError(player, exception);
                        openEnchantments(player, toolId, level, 0);
                    }
                },
                () -> openEnchantments(player, toolId, level, 0));
    }

    private void promptLore(Player player, String toolId, int level) {
        prompts.begin(player,
                messages.parse("<aqua><bold>Level lore</bold></aqua> <gray>Enter MiniMessage lore and separate lines with <white>;;</white>. Placeholders include <white>{level}, {current}, {required}, {goal_type_description}, {bound_world}, {owner_name}, {progress_bar}</white>.</gray>"),
                input -> {
                    try {
                        List<String> lore = java.util.Arrays.stream(input.split(";;", -1))
                                .map(String::trim)
                                .filter(line -> !line.isEmpty())
                                .toList();
                        lore.forEach(messages::parse);
                        change(player, toolId, "level lore",
                                () -> tools.setLevelLore(toolId, level, lore),
                                () -> openLore(player, toolId, level, 0));
                    } catch (RuntimeException exception) {
                        showError(player, exception);
                        openLore(player, toolId, level, 0);
                    }
                },
                () -> openLore(player, toolId, level, 0));
    }

    private void promptLoreLine(Player player, String toolId, int level, int lineIndex) {
        String verb = lineIndex < 0 ? "New" : "Edit";
        prompts.begin(player,
                messages.parse("<aqua><bold>" + verb + " lore line</bold></aqua> <gray>Enter one MiniMessage-formatted line. Placeholders include <white>{level}, {current}, {required}, {percentage}, {goal_type_description}, {target_progress}, {bound_world}, {owner_name}, {progress_bar}</white>.</gray>"),
                input -> {
                    try {
                        messages.parse(input);
                        ToolLevel current = tools.find(toolId).orElseThrow().level(level).orElseThrow();
                        List<String> lore = new ArrayList<>(current.lore());
                        if (lineIndex < 0) {
                            lore.add(input);
                        } else if (lineIndex < lore.size()) {
                            lore.set(lineIndex, input);
                        } else {
                            throw new IllegalArgumentException("That lore line no longer exists.");
                        }
                        tools.setLevelLore(toolId, level, lore);
                        saved(player, toolId, "level lore");
                        int page = Math.max(0, (lineIndex < 0 ? lore.size() - 1 : lineIndex) / GRID_SLOTS.length);
                        openLore(player, toolId, level, page);
                    } catch (Exception exception) {
                        showError(player, exception);
                        openLore(player, toolId, level, 0);
                    }
                },
                () -> openLore(player, toolId, level, 0));
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
            change(player, toolId, "level material inheritance", () -> tools.setLevelMaterial(toolId, level, null),
                    () -> openLevelEditor(player, toolId, level));
            return;
        }
        Material material = selectedMaterial(player, event);
        if (material == null) {
            showError(player, new IllegalArgumentException("Put an item on your cursor or in your main hand first."));
            return;
        }
        change(player, toolId, "level material", () -> tools.setLevelMaterial(toolId, level, material),
                () -> openLevelEditor(player, toolId, level));
    }

    private void toggleUnbreakable(Player player, String toolId, int level) {
        ToolLevel current = tools.find(toolId).orElseThrow().level(level).orElseThrow();
        change(player, toolId, "unbreakable setting",
                () -> tools.setLevelUnbreakable(toolId, level, !current.unbreakable()),
                () -> openLevelEditor(player, toolId, level));
    }

    private void cycleGlint(Player player, String toolId, int level) {
        ToolLevel current = tools.find(toolId).orElseThrow().level(level).orElseThrow();
        change(player, toolId, "enchantment glint",
                () -> tools.setLevelGlint(toolId, level, current.glint().next()),
                () -> openLevelEditor(player, toolId, level));
    }

    private void toggleHideEnchantments(Player player, String toolId, int level) {
        ToolLevel current = tools.find(toolId).orElseThrow().level(level).orElseThrow();
        change(player, toolId, "hidden enchantments",
                () -> tools.setLevelHideEnchantments(toolId, level, !current.hideEnchantments()),
                () -> openLevelEditor(player, toolId, level));
    }

    private void toggleHideAttributes(Player player, String toolId, int level) {
        ToolLevel current = tools.find(toolId).orElseThrow().level(level).orElseThrow();
        change(player, toolId, "hidden attributes",
                () -> tools.setLevelHideAttributes(toolId, level, !current.hideAttributes()),
                () -> openLevelEditor(player, toolId, level));
    }

    private void editCustomModelData(Player player, String toolId, int level, InventoryClickEvent event) {
        if (event.isRightClick()) {
            change(player, toolId, "custom model data",
                    () -> tools.setLevelCustomModelData(toolId, level, null),
                    () -> openLevelEditor(player, toolId, level));
            return;
        }
        prompts.begin(player,
                messages.parse("<aqua><bold>Custom model data</bold></aqua> <gray>Enter a non-negative whole number.</gray>"),
                input -> {
                    try {
                        int value = Integer.parseInt(input.replace("_", "").replace(",", ""));
                        change(player, toolId, "custom model data",
                                () -> tools.setLevelCustomModelData(toolId, level, value),
                                () -> openLevelEditor(player, toolId, level));
                    } catch (NumberFormatException exception) {
                        showError(player, new IllegalArgumentException("Custom model data must be a whole number."));
                        openLevelEditor(player, toolId, level);
                    }
                },
                () -> openLevelEditor(player, toolId, level));
    }

    private void adjustEnchantment(
            Player player,
            String toolId,
            int level,
            String enchantmentKey,
            InventoryClickEvent event
    ) {
        ToolLevel current = tools.find(toolId).orElseThrow().level(level).orElseThrow();
        int value = current.enchantments().entrySet().stream()
                .filter(entry -> entry.getKey().getKey().toString().equals(enchantmentKey))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(0);
        int updated;
        if (event.isShiftClick() && event.isRightClick()) {
            updated = 0;
        } else if (event.isShiftClick()) {
            updated = Math.min(255, value + 5);
        } else if (event.isRightClick()) {
            updated = Math.max(0, value - 1);
        } else {
            updated = Math.min(255, value + 1);
        }
        change(player, toolId, "level enchantment",
                () -> tools.setLevelEnchantment(toolId, level, enchantmentKey, updated),
                () -> openEnchantments(player, toolId, level, event.getView().getTopInventory().getHolder() instanceof PlexonGuiHolder gui ? gui.page() : 0));
    }

    private void clearEnchantments(Player player, String toolId, int level, InventoryClickEvent event) {
        if (!event.isShiftClick() || !event.isRightClick()) {
            showError(player, new IllegalArgumentException("Shift-right-click to clear every enchantment."));
            return;
        }
        change(player, toolId, "level enchantments",
                () -> tools.setLevelEnchantments(toolId, level, Map.of()),
                () -> openEnchantments(player, toolId, level, 0));
    }

    private void editLoreLine(
            Player player,
            String toolId,
            int level,
            int lineIndex,
            InventoryClickEvent event
    ) {
        ToolLevel current = tools.find(toolId).orElseThrow().level(level).orElseThrow();
        if (lineIndex < 0 || lineIndex >= current.lore().size()) {
            openLore(player, toolId, level, 0);
            return;
        }
        if (!event.isShiftClick() && !event.isRightClick()) {
            promptLoreLine(player, toolId, level, lineIndex);
            return;
        }

        List<String> lore = new ArrayList<>(current.lore());
        int destination = lineIndex;
        if (event.isShiftClick()) {
            destination = event.isRightClick()
                    ? Math.min(lore.size() - 1, lineIndex + 1)
                    : Math.max(0, lineIndex - 1);
            if (destination == lineIndex) {
                openLore(player, toolId, level, lineIndex / GRID_SLOTS.length);
                return;
            }
            java.util.Collections.swap(lore, lineIndex, destination);
        } else {
            lore.remove(lineIndex);
            destination = Math.min(lineIndex, Math.max(0, lore.size() - 1));
        }
        int targetPage = destination / GRID_SLOTS.length;
        change(player, toolId, "level lore",
                () -> tools.setLevelLore(toolId, level, lore),
                () -> openLore(player, toolId, level, targetPage));
    }

    private void clearLore(Player player, String toolId, int level, InventoryClickEvent event) {
        if (!event.isShiftClick() || !event.isRightClick()) {
            showError(player, new IllegalArgumentException("Shift-right-click to clear every lore line."));
            return;
        }
        change(player, toolId, "level lore",
                () -> tools.setLevelLore(toolId, level, List.of()),
                () -> openLore(player, toolId, level, 0));
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

    private void duplicateLevel(Player player, String toolId, int level) {
        try {
            int number = tools.duplicateLevel(toolId, level);
            saved(player, toolId, "duplicated level " + number);
            openLevelEditor(player, toolId, number);
        } catch (Exception exception) {
            showError(player, exception);
            openLevelEditor(player, toolId, level);
        }
    }

    private void moveLevel(Player player, String toolId, int level, int direction) {
        try {
            int destination = tools.moveLevel(toolId, level, direction);
            saved(player, toolId, "level order");
            openLevelEditor(player, toolId, destination);
        } catch (Exception exception) {
            showError(player, exception);
            openLevelEditor(player, toolId, level);
        }
    }

    private void removeLevel(Player player, String toolId, int level, InventoryClickEvent event) {
        if (!event.isShiftClick() || !event.isRightClick()) {
            showError(player, new IllegalArgumentException("Shift-right-click to confirm deleting this level."));
            return;
        }
        change(player, toolId, "levels", () -> tools.removeLevel(toolId, level),
                () -> openLevels(player, toolId, 0));
    }

    private void giveSelf(Player player, String toolId) {
        ToolDefinition definition = tools.find(toolId).orElse(null);
        if (definition == null) {
            showError(player, new IllegalArgumentException("That tool no longer exists."));
            openAdminList(player, 0);
            return;
        }
        ToolGrantService.GrantResult grantResult = grants.grant(player, definition, true);
        if (grantResult == ToolGrantService.GrantResult.INVALID_WORLD) {
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

    private ItemStack worldToolCard(
            Player player,
            ToolDefinition tool,
            String worldName,
            boolean active,
            boolean panelAvailable
    ) {
        Map<String, String> values = worldMenuPlaceholders(
                player, tool, worldName, active, panelAvailable);
        Material toolMaterial = savedToolMaterial(player, tool, worldName);
        PluginSettings.MenuItemTemplate template = settings.worldMenuToolCard();
        return styledWorldMenuItem(template, toolMaterial, values,
                active && settings.worldMenuToolCardActiveGlint(),
                panelAvailable ? "noop" : "toggle-world-tool", tool.id());
    }

    private ItemStack worldTogglePanel(
            Player player,
            ToolDefinition tool,
            String worldName,
            boolean active
    ) {
        Map<String, String> values = worldMenuPlaceholders(
                player, tool, worldName, active, true);
        PluginSettings.MenuItemTemplate template = active
                ? settings.worldMenuActivePanel() : settings.worldMenuInactivePanel();
        return styledWorldMenuItem(template, tool.firstLevel().material(), values,
                active, "toggle-world-tool", tool.id());
    }

    private ItemStack styledWorldMenuItem(
            PluginSettings.MenuItemTemplate template,
            Material toolMaterial,
            Map<String, String> values,
            boolean glint,
            String action,
            String value
    ) {
        List<Component> lore = template.lore().stream()
                .map(line -> line.isEmpty() ? Component.empty() : messages.parse(line, values))
                .toList();
        ItemStack item = ItemFactory.create(template.resolveMaterial(toolMaterial),
                messages.parse(template.displayName(), values), lore, glint);
        tag(item, action, value);
        return item;
    }

    private Map<String, String> worldMenuPlaceholders(
            Player player,
            ToolDefinition tool,
            String worldName,
            boolean active,
            boolean panelAvailable
    ) {
        ToolState state = activations.stateFor(player, tool, worldName).orElseGet(() ->
                new ToolState(tool.id(), new UUID(0L, 0L), tool.firstLevel().number(),
                        0L, worldName, player.getUniqueId(), tool.category(), Map.of()));
        Map<String, String> values = new HashMap<>(itemService.placeholders(tool, state));
        values.put("world", messages.plain(worldName));
        values.put("status", active
                ? "<green><bold>ACTIVE</bold></green>"
                : "<red><bold>INACTIVE</bold></red>");
        values.put("state", active ? "active" : "inactive");
        values.put("state_symbol", active ? "✔" : "✘");
        values.put("toggle_action", active ? "deactivate" : "activate");
        values.put("toggle_hint", panelAvailable
                ? "<dark_gray>Use the panel directly below.</dark_gray>"
                : "<yellow>Click this tool to " + (active ? "deactivate" : "activate") + ".</yellow>");
        return values;
    }

    private Material savedToolMaterial(Player player, ToolDefinition tool, String worldName) {
        return activations.stateFor(player, tool, worldName)
                .flatMap(value -> tool.level(value.level()))
                .map(ToolLevel::material)
                .orElse(tool.firstLevel().material());
    }

    private WorldMenuLayout worldMenuLayout(WorldToolMenu menu, String worldName) {
        List<ToolDefinition> available = tools.all().stream()
                .filter(tool -> activations.isAvailable(tool, worldName))
                .sorted(Comparator.comparing(ToolDefinition::id))
                .toList();
        Map<String, ToolDefinition> byId = available.stream().collect(Collectors.toMap(
                ToolDefinition::id, definition -> definition, (first, ignored) -> first,
                LinkedHashMap::new));
        Map<String, Integer> toolSlots = new LinkedHashMap<>();
        menu.toolSlots().forEach((toolId, slot) -> {
            if (byId.containsKey(toolId)) {
                toolSlots.put(toolId, slot);
            }
        });

        Set<Integer> occupied = new LinkedHashSet<>(toolSlots.values());
        Set<Integer> reservedPanels = new LinkedHashSet<>();
        if (settings.worldMenuTogglePanelEnabled()) {
            for (int slot : toolSlots.values()) {
                int panel = slot + 9;
                if (panel < menu.size() && !occupied.contains(panel)) {
                    reservedPanels.add(panel);
                }
            }
        }

        int omitted = 0;
        for (ToolDefinition definition : available) {
            if (toolSlots.containsKey(definition.id())) {
                continue;
            }
            int slot = nextAutomaticToolSlot(menu, occupied, reservedPanels);
            if (slot < 0) {
                omitted++;
                continue;
            }
            toolSlots.put(definition.id(), slot);
            occupied.add(slot);
            if (settings.worldMenuTogglePanelEnabled()) {
                int panel = slot + 9;
                if (panel < menu.size() && !occupied.contains(panel)
                        && !reservedPanels.contains(panel)) {
                    reservedPanels.add(panel);
                }
            }
        }

        Map<String, Integer> panelSlots = new LinkedHashMap<>();
        if (settings.worldMenuTogglePanelEnabled()) {
            Set<Integer> cards = Set.copyOf(toolSlots.values());
            Set<Integer> usedPanels = new LinkedHashSet<>();
            toolSlots.forEach((toolId, slot) -> {
                int candidate = slot + 9;
                if (candidate < menu.size() && !cards.contains(candidate)
                        && usedPanels.add(candidate)) {
                    panelSlots.put(toolId, candidate);
                }
            });
        }
        return new WorldMenuLayout(Map.copyOf(toolSlots), Map.copyOf(panelSlots), omitted);
    }

    private int nextAutomaticToolSlot(
            WorldToolMenu menu,
            Set<Integer> occupied,
            Set<Integer> reservedPanels
    ) {
        int[] content = contentSlots(menu.rows());
        if (settings.worldMenuTogglePanelEnabled()) {
            for (int slot : content) {
                int panel = slot + 9;
                if (!occupied.contains(slot) && !reservedPanels.contains(slot)
                        && panel < menu.size() && !occupied.contains(panel)
                        && !reservedPanels.contains(panel)) {
                    return slot;
                }
            }
        }
        for (int slot : content) {
            if (!occupied.contains(slot) && !reservedPanels.contains(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private ItemStack showcaseIcon(Player player, ToolDefinition tool) {
        Optional<ToolState> owned = itemService.findBestOwned(player, tool.id());
        boolean worldAllowed = tool.isAllowedWorld(player.getWorld().getName());
        boolean boundHere = owned.map(state -> state.boundWorld().equalsIgnoreCase(player.getWorld().getName())).orElse(true);
        boolean locked = !worldAllowed || !boundHere;
        Material resolvedMaterial = owned.flatMap(state -> tool.level(state.level()))
                .map(ToolLevel::material)
                .orElse(tool.firstLevel().material());
        Material material = locked ? Material.GRAY_DYE : resolvedMaterial;
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
            lore.add(messages.parse("<gray>Name:</gray> " + next.displayName()));
            lore.add(messages.parse("<gray>Material:</gray> <white>" + next.material().name() + "</white>"));
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
        return button("editor", tool.id(), tool.firstLevel().material(), tool.displayName(),
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

    private ItemStack toggleButton(String action, String value, Material material, String label, boolean enabled) {
        return button(action, value, material,
                (enabled ? "<green>" : "<red>") + "<bold>" + label + "</bold>" + (enabled ? "</green>" : "</red>"),
                "<gray>Current:</gray> " + (enabled ? "<green>enabled</green>" : "<red>disabled</red>"), "",
                "<gray>Click to toggle this setting.</gray>");
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
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private void fill(Inventory inventory, Material material, String name) {
        Component displayName = name == null || name.isBlank()
                ? Component.text(" ") : messages.parse(name);
        ItemStack filler = ItemFactory.create(material, displayName, List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private void addNavigation(Inventory inventory, int page, int pages, String action) {
        if (page > 0) {
            inventory.setItem(inventory.getSize() - 8, button(action, Integer.toString(page - 1),
                    Material.SPECTRAL_ARROW,
                    "<gradient:#FFF176:#FF8F00><bold>PlexonTools</bold></gradient> <dark_gray>•</dark_gray> <yellow>Previous page</yellow>",
                    "<gray>Page " + page + " of " + pages + "</gray>"));
        }
        if (page + 1 < pages) {
            inventory.setItem(inventory.getSize() - 2, button(action, Integer.toString(page + 1),
                    Material.SPECTRAL_ARROW,
                    "<gradient:#FFF176:#FF8F00><bold>PlexonTools</bold></gradient> <dark_gray>•</dark_gray> <yellow>Next page</yellow>",
                    "<gray>Page " + (page + 2) + " of " + pages + "</gray>"));
        }
    }

    private void openNextTick(Player player, Runnable action) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                action.run();
            }
        });
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

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static long adjustAmount(long current, long delta) {
        if (delta > 0L && Long.MAX_VALUE - current < delta) {
            return Long.MAX_VALUE;
        }
        if (delta < 0L && current < 1L - delta) {
            return 1L;
        }
        return Math.max(1L, current + delta);
    }

    private static String summarize(List<String> values, int maximum) {
        if (values.isEmpty()) {
            return "All";
        }
        String result = values.stream().limit(maximum).collect(Collectors.joining(", "));
        return values.size() > maximum ? result + " +" + (values.size() - maximum) : result;
    }

    private static String requirementSummary(LevelRequirement requirement) {
        if (requirement.mode() == RequirementMode.GENERAL) {
            return (requirement.isLegacyFilteredGeneral() ? "General filtered total • " : "General total • ")
                    + requirement.amount();
        }
        return requirement.targets().isEmpty()
                ? "Specific quotas • no targets"
                : "Specific quotas • " + requirement.targets().size() + " targets • "
                        + requirement.requiredTotal() + " combined";
    }

    private static Material targetIcon(TrackingType trackingType, String target) {
        if (trackingType.usesMaterialTargets()) {
            Material material = Material.matchMaterial(target);
            return material != null && material.isItem() && !material.isAir()
                    ? material : Material.PAPER;
        }
        Material spawnEgg = Material.matchMaterial(target + "_SPAWN_EGG");
        return spawnEgg != null && spawnEgg.isItem() ? spawnEgg : Material.NAME_TAG;
    }

    private static Material abilityMaterial(ToolAbilityType type) {
        return switch (type) {
            case AUTO_SMELT -> Material.BLAST_FURNACE;
            case AREA_MINE_3X3 -> Material.DIAMOND_PICKAXE;
            case EXP_BOOSTER -> Material.EXPERIENCE_BOTTLE;
            case MOB_POTION_EFFECT -> Material.POTION;
            case MAGNET -> Material.HOPPER;
        };
    }

    private static String humanizeKey(String key) {
        String value = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        return java.util.Arrays.stream(value.split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
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

    private record WorldMenuLayout(
            Map<String, Integer> toolSlots,
            Map<String, Integer> panelSlots,
            int omittedTools
    ) {
    }
}
