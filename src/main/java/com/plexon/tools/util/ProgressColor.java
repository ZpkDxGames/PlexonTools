package com.plexon.tools.util;

import java.util.Locale;

/**
 * Produces a smooth three-stop colour for a progress value.
 */
public final class ProgressColor {
    public static final String DEFAULT_START = "#FF5252";
    public static final String DEFAULT_MIDDLE = "#FFD740";
    public static final String DEFAULT_COMPLETE = "#76FF03";

    private ProgressColor() {
    }

    public static String forProgress(
            long current,
            long required,
            String start,
            String middle,
            String complete
    ) {
        double ratio = required <= 0L
                ? 1.0D
                : Math.max(0.0D, Math.min(1.0D, current / (double) required));
        int startRgb = parse(start, DEFAULT_START);
        int middleRgb = parse(middle, DEFAULT_MIDDLE);
        int completeRgb = parse(complete, DEFAULT_COMPLETE);
        int rgb = ratio < 0.5D
                ? interpolate(startRgb, middleRgb, ratio * 2.0D)
                : interpolate(middleRgb, completeRgb, (ratio - 0.5D) * 2.0D);
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    private static int parse(String configured, String fallback) {
        String value = configured == null ? "" : configured.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (!value.matches("(?i)[0-9a-f]{6}")) {
            value = fallback.substring(1);
        }
        return Integer.parseInt(value, 16);
    }

    private static int interpolate(int from, int to, double amount) {
        int red = channel(from >> 16, to >> 16, amount);
        int green = channel(from >> 8, to >> 8, amount);
        int blue = channel(from, to, amount);
        return red << 16 | green << 8 | blue;
    }

    private static int channel(int from, int to, double amount) {
        return (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
    }
}
