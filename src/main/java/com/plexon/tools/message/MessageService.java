package com.plexon.tools.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class MessageService {
    private static final Map<String, String> BUILT_IN_DEFAULTS = Map.of(
            "activation-inventory-full", "<yellow>Your inventory is full. Free one slot to activate the bound tool.</yellow>",
            "target-inventory-full", "<yellow><white>{player}</white>'s inventory is full; no tool was issued or dropped.</yellow>",
            "activation-unavailable", "<red>That tool cannot be activated in this world.</red>",
            "tool-activated", "<#9CCC65>✔ Equipped <white>{tool}</white> for <white>{world}</white>.</#9CCC65>",
            "tool-deactivated", "<#FFD54F>◆ Safely stored <white>{tool}</white> for <white>{world}</white>.</#FFD54F>",
            "progress-update", "<#FFD54F><bold>Lv. {level}</bold></#FFD54F> <dark_gray>•</dark_gray> {progress_bar} <white>{current}</white><dark_gray>/</dark_gray><#AEEA00>{required}</#AEEA00> <gray>({percentage}%)</gray>",
            "backup-started", "<gray>Draining pending writes and creating a consistent SQLite backup…</gray>",
            "backup-complete", "<green>Database backup created:</green> <white>{file}</white>",
            "backup-failed", "<red>The database backup failed. Check the console; the live database remains active.</red>"
    );
    private final JavaPlugin plugin;
    private final File file;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration messages = new YamlConfiguration();
    private String prefix = "";

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
    }

    public void reload() throws IOException, InvalidConfigurationException {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(file);
        messages = candidate;
        prefix = messages.getString("messages.prefix", messages.getString("prefix", ""));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String body = value(key);
        sender.sendMessage(parse(prefix + body, placeholders));
    }

    public void sendWithoutPrefix(Audience audience, String key, Map<String, String> placeholders) {
        String body = value(key);
        audience.sendMessage(parse(body, placeholders));
    }

    public void actionBar(Player player, String key, Map<String, String> placeholders) {
        String body = value(key);
        player.sendActionBar(parse(body, placeholders));
    }

    public Component parse(String input) {
        return miniMessage.deserialize(normalizeItalics(input));
    }

    public Component parse(String input, Map<String, String> placeholders) {
        String rendered = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
            rendered = rendered.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return miniMessage.deserialize(normalizeItalics(rendered));
    }

    public String plain(String value) {
        return miniMessage.escapeTags(value == null ? "" : value);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    private String value(String key) {
        return messages.getString("messages." + key,
                messages.getString(key, BUILT_IN_DEFAULTS.getOrDefault(key,
                        "<red>Missing message: " + plain(key) + "</red>")));
    }

    private static String normalizeItalics(String input) {
        String value = input == null ? "" : input;
        return value.startsWith("<!italic>") ? value : "<!italic>" + value;
    }
}
