package com.plexon.tools.service;

import com.plexon.tools.message.MessageService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class ChatPromptService implements Listener {
    private static final long TIMEOUT_TICKS = 20L * 60L;

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final Map<UUID, PendingPrompt> prompts = new ConcurrentHashMap<>();

    public ChatPromptService(JavaPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void begin(Player player, Component question, Consumer<String> input, Runnable cancelled) {
        player.closeInventory();
        PendingPrompt prompt = new PendingPrompt(UUID.randomUUID(), input, cancelled);
        prompts.put(player.getUniqueId(), prompt);
        player.sendMessage(question);
        player.sendMessage(messages.parse("<dark_gray>Type <white>cancel</white> to stop. This prompt expires in 60 seconds.</dark_gray>"));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingPrompt active = prompts.get(player.getUniqueId());
            if (active != null && active.token().equals(prompt.token())) {
                prompts.remove(player.getUniqueId());
                messages.send(player, "prompt-timeout");
                cancelled.run();
            }
        }, TIMEOUT_TICKS);
    }

    public void cancelAll() {
        prompts.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        PendingPrompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String value = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (value.equalsIgnoreCase("cancel")) {
                messages.send(event.getPlayer(), "prompt-cancelled");
                prompt.cancelled().run();
                return;
            }
            try {
                prompt.input().accept(value);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Editor prompt callback failed", exception);
                messages.send(event.getPlayer(), "editor-error",
                        Map.of("error", messages.plain(exception.getMessage() == null
                                ? "Could not apply that value." : exception.getMessage())));
                prompt.cancelled().run();
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    private record PendingPrompt(UUID token, Consumer<String> input, Runnable cancelled) {
    }
}
