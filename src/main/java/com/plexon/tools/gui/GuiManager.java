package com.plexon.tools.gui;

import com.plexon.tools.config.PluginSettings;
import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.item.ToolItemService;
import com.plexon.tools.item.ToolState;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.model.LevelRequirement;
import com.plexon.tools.model.RequirementMode;
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
    private final Map<UUID, String> targetSearches = new HashMap<>();

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
            long cumulative = cumulativeRequirement(tool, level.number());
            inventory.setItem(GRID_SLOTS[index], button("edit-level", Integer.toString(level.number()), level.material(),
                    "<gradient:#41E296:#A8FF78><bold>Level " + level.number() + "</bold></gradient>",
                    level.displayName(),
                    "<dark_gray>" + (level.displayNameOverride() ? "Custom name" : "Inherited name") + "</dark_gray>", "",
                    "<gray>Progress before level:</gray> <white>" + cumulative + "</white>",
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
                "<gray>Cumulative before this level:</gray> <white>" + cumulativeRequirement(tool, level.number()) + "</white>", "",
                "<gray>Configure mode, exact amounts, step</gray>",
                "<gray>controls, target search, and quotas.</gray>"));
        inventory.setItem(13, button("level-enchantments", tool.id(), Material.ENCHANTED_BOOK,
                "<light_purple><bold>Enchantments</bold></light_purple>",
                "<white>" + formatEnchantments(level.enchantments()) + "</white>", "",
                "<gray>Open the visual enchantment editor.</gray>"));
        inventory.setItem(14, button("level-lore", tool.id(), Material.BOOK,
                "<aqua><bold>Lore</bold></aqua>", "<white>" + level.lore().size() + " line(s)</white>", "",
                "<gray>Edit, move, add, or delete individual lines.</gray>"));
        inventory.setItem(15, toggleButton("level-unbreakable", tool.id(), Material.OBSIDIAN,
                "Unbreakable", level.unbreakable()));
        inventory.setItem(16, button("level-glint", tool.id(), Material.GLOW_INK_SAC,
                "<light_purple><bold>Enchantment glint</bold></light_purple>",
                "<white>" + level.glint().displayName() + "</white>", "",
                "<gray>Click to cycle automatic, on, and off.</gray>"));
        inventory.setItem(19, toggleButton("level-hide-enchants", tool.id(), Material.BOOKSHELF,
                "Hide enchantments", level.hideEnchantments()));
        inventory.setItem(20, toggleButton("level-hide-attributes", tool.id(), Material.IRON_CHESTPLATE,
                "Hide attributes", level.hideAttributes()));
        inventory.setItem(21, button("level-model-data", tool.id(), Material.PAINTING,
                "<aqua><bold>Custom model data</bold></aqua>",
                "<white>" + (level.customModelData() == null ? "Not set" : level.customModelData()) + "</white>", "",
                "<gray>Left-click to set an integer.</gray>", "<gray>Right-click to clear.</gray>"));
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
            case "requirements" -> openRequirement(player, value, 1);
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
            case "target-page" -> openTargetSelector(
                    player, holder.toolId(), holder.level(), parseInt(value, 0));
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
            case "duplicate-level" -> duplicateLevel(player, holder.toolId(), holder.level());
            case "move-level-up" -> moveLevel(player, holder.toolId(), holder.level(), -1);
            case "move-level-down" -> moveLevel(player, holder.toolId(), holder.level(), 1);
            case "remove-level" -> removeLevel(player, holder.toolId(), holder.level(), event);
            case "enchantments-page" -> openEnchantments(player, holder.toolId(), holder.level(), parseInt(value, 0));
            case "adjust-enchantment" -> adjustEnchantment(player, holder.toolId(), holder.level(), value, event);
            case "enchantments-bulk" -> promptEnchantments(player, holder.toolId(), holder.level());
            case "clear-enchantments" -> clearEnchantments(player, holder.toolId(), holder.level(), event);
            case "lore-page" -> openLore(player, holder.toolId(), holder.level(), parseInt(value, 0));
            case "lore-line" -> editLoreLine(player, holder.toolId(), holder.level(), parseInt(value, 0), event);
            case "add-lore-line" -> promptLoreLine(player, holder.toolId(), holder.level(), -1);
            case "lore-bulk" -> promptLore(player, holder.toolId(), holder.level());
            case "clear-lore" -> clearLore(player, holder.toolId(), holder.level(), event);
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
                "<gray>Click to toggle this level setting.</gray>");
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
            inventory.setItem(inventory.getSize() - 8, button(action, Integer.toString(page - 1), Material.ARROW,
                    "<yellow><bold>Previous page</bold></yellow>", "<gray>Page " + page + " of " + pages + "</gray>"));
        }
        if (page + 1 < pages) {
            inventory.setItem(inventory.getSize() - 2, button(action, Integer.toString(page + 1), Material.ARROW,
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

    private static long cumulativeRequirement(ToolDefinition tool, int level) {
        long total = 0L;
        for (ToolLevel profile : tool.levels().headMap(level, false).values()) {
            long amount = profile.requirement().requiredTotal();
            total = Long.MAX_VALUE - total < amount ? Long.MAX_VALUE : total + amount;
        }
        return total;
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
        if (trackingType == TrackingType.BLOCKS_BROKEN) {
            Material material = Material.matchMaterial(target);
            return material != null && material.isItem() && !material.isAir()
                    ? material : Material.PAPER;
        }
        Material spawnEgg = Material.matchMaterial(target + "_SPAWN_EGG");
        return spawnEgg != null && spawnEgg.isItem() ? spawnEgg : Material.NAME_TAG;
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
}
