package com.plexon.tools.message;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MessageServiceTest {
    @Test
    void rendersBraceAndLegacyAnglePlaceholdersInOnePass() {
        assertEquals("Hello Ada — 42", MessageService.renderPlaceholders(
                "Hello {player} — <amount>",
                Map.of("player", "Ada", "amount", "42")));
    }

    @Test
    void leavesMiniMessageTagsAndUnknownPlaceholdersUntouched() {
        assertEquals("<gray>Hello {missing}</gray>", MessageService.renderPlaceholders(
                "<gray>Hello {missing}</gray>", Map.of("player", "Ada")));
    }

    @Test
    void resolvesPlaceholdersInsideMiniMessageTagArguments() {
        assertEquals(
                "<click:run_command:'/msg Ada'><green>Open</green></click>",
                MessageService.renderPlaceholders(
                        "<click:run_command:'/msg {player}'>{label}</click>",
                        Map.of("player", "Ada", "label", "<green>Open</green>")));
    }

    @Test
    void handlesNullInputAndPlaceholderMap() {
        assertEquals("", MessageService.renderPlaceholders(null, Map.of()));
        assertEquals("unchanged", MessageService.renderPlaceholders("unchanged", null));
    }
}
