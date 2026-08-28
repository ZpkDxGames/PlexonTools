package com.plexon.tools.command;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlexonToolsCommand implements TabExecutor {
    private final ToolConfigRepository tools;
    private final ToolGrantService grants;
    private final GuiManager gui;
    private final MessageService messages;
    private final ReloadAction reloadAction;

    public PlexonToolsCommand(
            ToolConfigRepository tools,
            ToolGrantService grants,
            GuiManager gui,
            MessageService messages,
            ReloadAction reloadAction
    ) {
        this.tools = tools;
        this.grants = grants;
        this.gui = gui;
        this.messages = messages;
        this.reloadAction = reloadAction;
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
            gui.openShowcase(player, 0);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "reload" -> reload(sender);
            case "gui" -> openAdmin(sender);
            default -> {
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("plexontools.give")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage(messages.parse("<yellow>Usage:</yellow> <white>/pt give <player> <tool_id></white>"));
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
        if (!grants.grant(target, definition, sender != target)) {
            messages.send(sender, "invalid-world", Map.of(
                    "tool", definition.displayName(),
                    "world", messages.plain(target.getWorld().getName())
            ));
            return true;
        }
        messages.send(sender, "tool-given", Map.of(
                "tool", definition.displayName(),
                "player", messages.plain(target.getName()),
                "world", messages.plain(target.getWorld().getName())
        ));
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
        gui.openAdminList(player, 0);
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(messages.parse("<gradient:#4158D0:#C850C0><bold>PlexonTools</bold></gradient> <gray>commands</gray>"));
        sender.sendMessage(messages.parse("<white>/" + messages.plain(label) + "</white> <dark_gray>—</dark_gray> <gray>Open the tool showcase</gray>"));
        if (sender.hasPermission("plexontools.admin")) {
            sender.sendMessage(messages.parse("<white>/" + messages.plain(label) + " gui</white> <dark_gray>—</dark_gray> <gray>Open the tool editor</gray>"));
            sender.sendMessage(messages.parse("<white>/" + messages.plain(label) + " give <player> <tool_id></white>"));
            sender.sendMessage(messages.parse("<white>/" + messages.plain(label) + " reload</white>"));
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
            if (sender.hasPermission("plexontools.give")) values.add("give");
            if (sender.hasPermission("plexontools.gui")) values.add("gui");
            if (sender.hasPermission("plexontools.reload")) values.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            tools.all().stream().map(ToolDefinition::id).forEach(values::add);
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
}
