package com.plexon.tools.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlintModeTest {
    @Test
    void supportsCanonicalAndLegacyValues() {
        assertEquals(GlintMode.AUTO, GlintMode.parse(null));
        assertEquals(GlintMode.AUTO, GlintMode.parse("auto"));
        assertEquals(GlintMode.ON, GlintMode.parse("enabled"));
        assertEquals(GlintMode.OFF, GlintMode.parse("false"));
    }

    @Test
    void cyclesThroughEveryMode() {
        assertEquals(GlintMode.ON, GlintMode.AUTO.next());
        assertEquals(GlintMode.OFF, GlintMode.ON.next());
        assertEquals(GlintMode.AUTO, GlintMode.OFF.next());
        assertNull(GlintMode.AUTO.override());
    }
}
