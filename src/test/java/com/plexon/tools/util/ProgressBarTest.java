package com.plexon.tools.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressBarTest {
    @Test
    void clampsProgressToBarWidth() {
        String bar = ProgressBar.render(10, 150, 100, "+", "-", "<green>", "<gray>");
        assertEquals("<green>++++++++++<reset><gray><reset>", bar);
    }

    @Test
    void rendersHalfProgress() {
        String bar = ProgressBar.render(10, 50, 100, "+", "-", "<green>", "<gray>");
        assertEquals("<green>+++++<reset><gray>-----<reset>", bar);
    }
}
