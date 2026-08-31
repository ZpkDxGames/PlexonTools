package com.plexon.tools.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProgressColorTest {
    @Test
    void followsConfiguredRedAmberGreenStops() {
        assertEquals("#FF5252", ProgressColor.forProgress(
                0, 100, "#FF5252", "#FFD740", "#76FF03"));
        assertEquals("#FFD740", ProgressColor.forProgress(
                50, 100, "#FF5252", "#FFD740", "#76FF03"));
        assertEquals("#76FF03", ProgressColor.forProgress(
                100, 100, "#FF5252", "#FFD740", "#76FF03"));
    }

    @Test
    void clampsProgressAndTreatsTerminalProfilesAsComplete() {
        assertEquals("#FF5252", ProgressColor.forProgress(
                -10, 100, "#FF5252", "#FFD740", "#76FF03"));
        assertEquals("#76FF03", ProgressColor.forProgress(
                200, 100, "#FF5252", "#FFD740", "#76FF03"));
        assertEquals("#76FF03", ProgressColor.forProgress(
                0, 0, "#FF5252", "#FFD740", "#76FF03"));
    }

    @Test
    void invalidConfiguredColorsFallBackSafely() {
        assertEquals("#FFD740", ProgressColor.forProgress(
                50, 100, "red", null, "green"));
    }
}
