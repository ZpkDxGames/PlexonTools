package com.plexon.tools.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgressionScopeTest {
    @Test
    void acceptsDocumentedSharedAndPerWorldAliases() {
        assertEquals(ProgressionScope.PLAYER, ProgressionScope.parse("player"));
        assertEquals(ProgressionScope.PLAYER, ProgressionScope.parse("cross-world"));
        assertEquals(ProgressionScope.WORLD, ProgressionScope.parse("world"));
        assertEquals(ProgressionScope.WORLD, ProgressionScope.parse("per_world"));
    }

    @Test
    void rejectsUnknownScopes() {
        assertThrows(IllegalArgumentException.class,
                () -> ProgressionScope.parse("dimension-ish"));
    }
}
