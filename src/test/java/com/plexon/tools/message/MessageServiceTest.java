package com.plexon.tools.message;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void resolvesDynamicProgressColorInsideColorTags() {
        assertEquals(
                "<color:#FF8A3D>42</color><gray>/100</gray>",
                MessageService.renderPlaceholders(
                        "<color:{current_color}>{current}</color><gray>/{required}</gray>",
                        Map.of("current_color", "#FF8A3D", "current", "42", "required", "100")));
    }

    @Test
    void resolvesEveryPlaceholderShapeSeenInDynamicRequirementLore() {
        assertEquals(
                "<color:#FF5252>0</color><gray>/</gray><#B0BEC5>15</#B0BEC5> "
                        + "<color:#FF5252>(0%)</color>",
                MessageService.renderPlaceholders(
                        "<color:{requirement_current_color}>{requirement_current}</color>"
                                + "<gray>/</gray><#B0BEC5>{requirement_required}</#B0BEC5> "
                                + "<color:{requirement_current_color}>"
                                + "({requirement_percentage}%)</color>",
                        Map.of(
                                "requirement_current_color", "#FF5252",
                                "requirement_current", "0",
                                "requirement_required", "15",
                                "requirement_percentage", "0")));
    }

    @Test
    void handlesNullInputAndPlaceholderMap() {
        assertEquals("", MessageService.renderPlaceholders(null, Map.of()));
        assertEquals("unchanged", MessageService.renderPlaceholders("unchanged", null));
    }

    @Test
    void reusesRenderedComponentsAndKeepsTheCacheBounded() {
        MessageService service = new MessageService(null, new File("unused-messages.yml"));

        Component first = service.parse("<gray>Hello {player}</gray>", Map.of("player", "Ada"));
        Component second = service.parse("<gray>Hello {player}</gray>", Map.of("player", "Ada"));
        assertSame(first, second);

        for (int index = 0; index < MessageService.RENDERED_COMPONENT_CACHE_LIMIT + 128; index++) {
            service.parse("<gray>Entry " + index + "</gray>");
        }
        assertTrue(service.renderedComponentCacheSize()
                <= MessageService.RENDERED_COMPONENT_CACHE_LIMIT);
    }
}
