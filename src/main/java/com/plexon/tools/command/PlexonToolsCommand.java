package com.plexon.tools.command;

import com.plexon.tools.config.CategoryRepository;
import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.gui.GuiManager;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.service.ToolGrantService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlexonToolsCommand implements TabExecutor {
    private final CategoryRepository categories;
    private final ToolConfigRepository tools;
    private final ToolGrantService grants;
    private final GuiManager gui;
    private final MessageService messages;
    private final ReloadAction reloadAction;
    private final BackupAction backupAction;

    public PlexonToolsCommand(
            CategoryRepository categories,
            ToolConfigRepository tools,
            ToolGrantService grants,
            GuiManager gui,
            MessageService messages,
            ReloadAction reloadAction,
            BackupAction backupAction
    ) {
        this.categories = categories;
        this.tools = tools;
        this.grants = grants;
        this.gui = gui;
        this.messages = messages;
        this.reloadAction = reloadAction;
        this.backupAction = backupAction;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "players-only");
                return true;
            }
            gui.openPlayerEntry(player, player);
            return true;
        }

        String route = args[0].toLowerCase(Locale.ROOT);
        return switch (route) {
            case "give" -> give(sender, args);
            case "reload" -> reload(sender);
            case "backup" -> backup(sender);
            case "gui" -> openAdmin(sender);
            case "all" -> openShowcase(sender, null, args);
            default -> openCategory(sender, route, args, label);
        };
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("plexontools.give")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3 || args.length > 4) {
            sender.sendMessage(messages.parse(
                    "<yellow>Usage:</yellow> <white>/pt give <player> <tool_id> [world]</white>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", Map.of("player", messages.plain(args[1])));
            return true;
        }
        ToolDefinition definition = tools.find(args[2]).orElse(null);
        if (definition == null) {
            messages.send(sender, "tool-not-found", Map.of("tool", messages.plain(args[2])));
            return true;
        }
        if (!definition.enabled()) {
            messages.send(sender, "tool-disabled");
            return true;
        }
        String boundWorld = args.length == 4 ? args[3] : target.getWorld().getName();
        ToolGrantService.GrantResult grantResult = grants.grant(
                target, definition, boundWorld, sender != target);
        if (grantResult == ToolGrantService.GrantResult.INVALID_WORLD) {
            messages.send(sender, "invalid-world", Map.of(
                    "tool", definition.displayName(),
                    "world", messages.plain(boundWorld)
            ));
            return true;
        }
        if (grantResult == ToolGrantService.GrantResult.INVENTORY_FULL) {
            messages.send(sender, "target-inventory-full", Map.of(
                    "player", messages.plain(target.getName())));
            return true;
        }
        messages.send(sender, "tool-given", Map.of(
                "tool", definition.displayName(),
                "player", messages.plain(target.getName()),
                "world", messages.plain(boundWorld)
        ));
        return true;
    }

    private boolean openCategory(
            CommandSender sender,
            String categoryId,
            String[] args,
            String label
    ) {
        if (categories.find(categoryId).isEmpty()) {
            messages.send(sender, "category-not-found", Map.of(
                    "category", messages.plain(categoryId)));
            sendUsage(sender, label);
            return true;
        }
        return openShowcase(sender, categoryId, args);
    }

    private boolean openShowcase(CommandSender sender, String categoryId, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(messages.parse(
                    "<yellow>Usage:</yellow> <white>/pt "
                            + messages.plain(categoryId == null ? "all" : categoryId)
                            + " [player]</white>"));
            return true;
        }

        Player viewer;
        Player target;
        if (args.length == 2) {
            if (!sender.hasPermission("plexontools.admin")) {
                messages.send(sender, "no-permission");
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                messages.send(sender, "player-not-found", Map.of(
                        "player", messages.plain(args[1])));
                return true;
            }
            viewer = sender instanceof Player player ? player : target;
        } else if (sender instanceof Player player) {
            viewer = player;
            target = player;
        } else {
            messages.send(sender, "players-only");
            return true;
        }

        gui.openShowcase(viewer, target, categoryId, 0);
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("plexontools.reload")) {
            messages.send(sender, "no-permission");
            return true;
        }
        long started = System.nanoTime();
        try {
            reloadAction.reload();
            long millis = (System.nanoTime() - started) / 1_000_000L;
            messages.send(sender, "reload-complete", Map.of(
                    "count", Integer.toString(tools.size()),
                    "time", Long.toString(millis)
            ));
        } catch (Exception exception) {
            messages.plugin().getLogger().log(java.util.logging.Level.SEVERE, "Reload failed", exception);
            messages.send(sender, "reload-failed");
        }
        return true;
    }

    private boolean openAdmin(CommandSender sender) {
        if (!sender.hasPermission("plexontools.gui")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        gui.openAdminDashboard(player);
        return true;
    }

    private boolean backup(CommandSender sender) {
        if (!sender.hasPermission("plexontools.backup")) {
            messages.send(sender, "no-permission");
            return true;
        }
        messages.send(sender, "backup-started");
        messages.plugin().getServer().getScheduler().runTaskAsynchronously(messages.plugin(), () -> {
            try {
                Path backup = backupAction.backup();
                messages.plugin().getServer().getScheduler().runTask(messages.plugin(), () ->
                        messages.send(sender, "backup-complete", Map.of(
                                "file", messages.plain(backup.toString()))));
            } catch (Exception exception) {
                messages.plugin().getLogger().log(
                        java.util.logging.Level.SEVERE, "Database backup failed", exception);
                messages.plugin().getServer().getScheduler().runTask(messages.plugin(), () ->
                        messages.send(sender, "backup-failed"));
            }
        });
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(messages.parse("<gradient:#4158D0:#C850C0><bold>PlexonTools</bold></gradient> <gray>commands</gray>"));
        sender.sendMessage(messages.parse("<white>/" + messages.plain(label)
                + "</white> <dark_gray>—</dark_gray> <gray>Open this world's tool activation menu</gray>"));
        sender.sendMessage(messages.parse("<white>/" + messages.plain(label)
                + " <category></white> <dark_gray>—</dark_gray> <gray>Open a legacy category showcase</gray>"));
        sender.sendMessage(messages.parse("<white>/" + messages.plain(label)
                + " all</white> <dark_gray>—</dark_gray> <gray>Browse every tool</gray>"));
        if (sender.hasPermission("plexontools.admin")) {
            sender.sendMessage(messages.parse("<white>/" + messages.plain(label) + " gui</white> <dark_gray>—</dark_gray> <gray>Open the tool editor</gray>"));
            sender.sendMessage(messages.parse("<white>/" + messages.plain(label)
                    + " <category|all> [player]</white>"));
            sender.sendMessage(messages.parse("<white>/" + messages.plain(label)
                    + " give <player> <tool_id> [world]</white>"));
            sender.sendMessage(messages.parse("<white>/" + messages.plain(label) + " reload</white>"));
            sender.sendMessage(messages.parse("<white>/" + messages.plain(label) + " backup</white>"));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (args.length == 1) {
            categories.all().forEach(category -> values.add(category.id()));
            values.add("all");
            if (sender.hasPermission("plexontools.give")) values.add("give");
            if (sender.hasPermission("plexontools.gui")) values.add("gui");
            if (sender.hasPermission("plexontools.reload")) values.add("reload");
            if (sender.hasPermission("plexontools.backup")) values.add("backup");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
        } else if (args.length == 2
                && sender.hasPermission("plexontools.admin")
                && (args[0].equalsIgnoreCase("all") || categories.find(args[0]).isPresent())) {
            Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            tools.all().stream().map(ToolDefinition::id).forEach(values::add);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            tools.find(args[2]).ifPresent(tool -> values.addAll(tool.allowedWorlds()));
        }
        String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(partial))
                .sorted()
                .toList();
    }

    @FunctionalInterface
    public interface ReloadAction {
        void reload() throws Exception;
    }

    @FunctionalInterface
    public interface BackupAction {
        Path backup() throws Exception;
    }
}
