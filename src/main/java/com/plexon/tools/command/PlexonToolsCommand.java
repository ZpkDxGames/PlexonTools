package com.plexon.tools.command;

import com.plexon.tools.config.CategoryRepository;
import com.plexon.tools.config.ToolConfigRepository;
import com.plexon.tools.gui.GuiManager;
import com.plexon.tools.message.MessageService;
import com.plexon.tools.model.ToolDefinition;
import com.plexon.tools.performance.MiningPerformanceProfiler;
import com.plexon.tools.performance.MiningPerformanceProfiler.Isolation;
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
    private final DiagnosticsAction diagnosticsAction;
    private final MiningPerformanceProfiler profiler;

    public PlexonToolsCommand(
            CategoryRepository categories,
            ToolConfigRepository tools,
            ToolGrantService grants,
            GuiManager gui,
            MessageService messages,
            ReloadAction reloadAction,
            BackupAction backupAction,
            DiagnosticsAction diagnosticsAction,
            MiningPerformanceProfiler profiler
    ) {
        this.categories = categories;
        this.tools = tools;
        this.grants = grants;
        this.gui = gui;
        this.messages = messages;
        this.reloadAction = reloadAction;
        this.backupAction = backupAction;
        this.diagnosticsAction = diagnosticsAction;
        this.profiler = profiler;
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
            case "diagnostics" -> diagnostics(sender);
            case "perf" -> performance(sender, args);
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
                    "time", Long.toString(millis),
                    "version", messages.plain(messages.plugin().getPluginMeta().getVersion())
            ));
        } catch (Exception exception) {
            messages.plugin().getLogger().log(java.util.logging.Level.SEVERE, "Reload failed", exception);
            messages.send(sender, "reload-failed");
        }
        return true;
    }

    private boolean diagnostics(CommandSender sender) {
        if (!sender.hasPermission("plexontools.diagnostics")) {
            messages.send(sender, "no-permission");
            return true;
        }
        diagnosticsAction.lines().forEach(line -> sender.sendMessage(messages.parse(line)));
        return true;
    }

    private boolean performance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("plexontools.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sendPerfUsage(sender);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "start" -> {
                if (args.length > 3) {
                    sendPerfUsage(sender);
                    return true;
                }
                int sampleLimit = 0;
                if (args.length == 3) {
                    try {
                        sampleLimit = Integer.parseInt(args[2]);
                    } catch (NumberFormatException exception) {
                        sender.sendMessage(messages.parse(
                                "<red>Sample limit must be a positive integer.</red>"));
                        return true;
                    }
                    if (sampleLimit <= 0) {
                        sender.sendMessage(messages.parse(
                                "<red>Sample limit must be greater than zero.</red>"));
                        return true;
                    }
                }
                profiler.startSession(sampleLimit);
                sender.sendMessage(messages.parse(
                        "<green><bold>PlexonTools mining profiler started.</bold></green>"
                                + (sampleLimit > 0
                                ? " <gray>Auto-stop: " + sampleLimit + " Plexon block samples.</gray>"
                                : " <gray>Use /pt perf stop when the benchmark ends.</gray>")));
                sender.sendMessage(messages.parse(
                        "<yellow>Profiling adds diagnostic overhead; compare only runs using the same mode.</yellow>"));
            }
            case "stop" -> {
                profiler.stopSession();
                sender.sendMessage(messages.parse(
                        "<yellow>PlexonTools mining profiler stopped. Isolation flags were cleared.</yellow>"));
            }
            case "reset" -> {
                profiler.reset();
                sender.sendMessage(messages.parse(
                        "<green>Mining profiler metrics reset.</green>"));
            }
            case "report" -> {
                List<String> lines = profiler.reportLines();
                lines.forEach(line -> sender.sendMessage(messages.parse(line)));
                if (args.length == 3 && args[2].equalsIgnoreCase("console")) {
                    lines.forEach(line -> messages.plugin().getLogger().info(stripMiniMessage(line)));
                    sender.sendMessage(messages.parse(
                            "<gray>Profile report also written to console.</gray>"));
                } else if (args.length > 2) {
                    sendPerfUsage(sender);
                }
            }
            case "status" -> {
                sender.sendMessage(messages.parse(
                        "<gradient:#66BB6A:#42A5F5><bold>PlexonTools Performance Diagnostics</bold></gradient>"));
                sender.sendMessage(messages.parse(
                        "<gray>Profiler:</gray> <white>"
                                + (profiler.enabled() ? "RUNNING" : "STOPPED") + "</white>"));
                sender.sendMessage(messages.parse(
                        "<gray>Samples:</gray> <white>" + profiler.blockSamples() + "</white>"));
                if (profiler.autoStopSamples() > 0) {
                    sender.sendMessage(messages.parse(
                            "<gray>Auto-stop:</gray> <white>" + profiler.autoStopSamples() + "</white>"));
                }
                profiler.isolationStatusLines().forEach(line -> sender.sendMessage(messages.parse(
                        "<dark_gray>•</dark_gray> <gray>" + line + "</gray>")));
            }
            case "isolate" -> isolate(sender, args);
            default -> sendPerfUsage(sender);
        }
        return true;
    }

    private void isolate(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sendPerfUsage(sender);
            return;
        }
        Isolation isolation = Isolation.parse(args[2]);
        if (isolation == null) {
            sender.sendMessage(messages.parse(
                    "<red>Unknown isolation stage.</red> <gray>Use tab completion or /pt perf status.</gray>"));
            return;
        }
        String rawState = args[3].toLowerCase(Locale.ROOT);
        boolean enabled;
        if (rawState.equals("on")) {
            enabled = true;
        } else if (rawState.equals("off")) {
            enabled = false;
        } else {
            sender.sendMessage(messages.parse("<red>Isolation state must be on or off.</red>"));
            return;
        }
        if (!profiler.enabled()) {
            sender.sendMessage(messages.parse(
                    "<red>Start a profiling session before enabling diagnostic isolation.</red>"));
            return;
        }
        profiler.setIsolation(isolation, enabled);
        sender.sendMessage(messages.parse(
                "<yellow><bold>DIAGNOSTIC MODE:</bold></yellow> <gray>"
                        + isolation.key() + " isolation is now </gray><white>"
                        + (enabled ? "ON" : "OFF") + "</white><gray>.</gray>"));
        sender.sendMessage(messages.parse(
                "<red>Isolation changes normal gameplay semantics and automatically resets on stop, reload, or restart.</red>"));
    }

    private void sendPerfUsage(CommandSender sender) {
        sender.sendMessage(messages.parse(
                "<gradient:#66BB6A:#42A5F5><bold>PlexonTools Performance Diagnostics</bold></gradient>"));
        sender.sendMessage(messages.parse("<white>/pt perf start [samples]</white>"));
        sender.sendMessage(messages.parse("<white>/pt perf stop</white>"));
        sender.sendMessage(messages.parse("<white>/pt perf reset</white>"));
        sender.sendMessage(messages.parse("<white>/pt perf report [console]</white>"));
        sender.sendMessage(messages.parse("<white>/pt perf status</white>"));
        sender.sendMessage(messages.parse("<white>/pt perf isolate <stage> <on|off></white>"));
    }

    private static String stripMiniMessage(String line) {
        return line.replaceAll("<[^>]+>", "");
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
            sender.sendMessage(messages.parse("<white>/" + messages.plain(label) + " diagnostics</white>"));
            sender.sendMessage(messages.parse("<white>/" + messages.plain(label) + " perf</white>"));
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
            if (sender.hasPermission("plexontools.diagnostics")) values.add("diagnostics");
            if (sender.hasPermission("plexontools.admin")) values.add("perf");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("perf")
                && sender.hasPermission("plexontools.admin")) {
            values.addAll(List.of("start", "stop", "reset", "report", "status", "isolate"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("perf")
                && args[1].equalsIgnoreCase("start")) {
            values.add("1000");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("perf")
                && args[1].equalsIgnoreCase("report")) {
            values.add("console");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("perf")
                && args[1].equalsIgnoreCase("isolate")) {
            for (Isolation isolation : Isolation.values()) {
                values.add(isolation.key());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("perf")
                && args[1].equalsIgnoreCase("isolate")) {
            values.addAll(List.of("on", "off"));
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

    @FunctionalInterface
    public interface DiagnosticsAction {
        List<String> lines();
    }
}
