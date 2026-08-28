package com.plexon.tools.util;

public final class ProgressBar {
    private ProgressBar() {
    }

    public static String render(
            int width,
            long current,
            long required,
            String filledSymbol,
            String emptySymbol,
            String filledFormat,
            String emptyFormat
    ) {
        int safeWidth = Math.max(1, width);
        double ratio = required <= 0L ? 1.0D : (double) Math.max(0L, current) / (double) required;
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        int filled = (int) Math.floor(ratio * safeWidth);
        int empty = safeWidth - filled;
        return filledFormat + filledSymbol.repeat(filled) + "<reset>"
                + emptyFormat + emptySymbol.repeat(empty) + "<reset>";
    }
}
