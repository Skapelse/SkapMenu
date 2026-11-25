package com.skapclient;

public enum Theme {
    // order: BG, PANEL, ROW, ACCENT, ACCENT2, TEXT, SUBTEXT
    PURPLE("Dark Purple", 0xFF0F0A1C, 0xFF181129, 0xFF21173A, 0xFF9B5CFF, 0xFFC084FC, 0xFFF1EAFF, 0xFF9D8FBF),
    BLUE("Dark Blue",     0xFF08101F, 0xFF0E1A30, 0xFF152643, 0xFF3D8BFF, 0xFF7DB4FF, 0xFFEAF2FF, 0xFF8FA6C9),
    RED("Dark Red",       0xFF1A0A0C, 0xFF281113, 0xFF3A171A, 0xFFE0313F, 0xFFFF6B75, 0xFFFFEBEC, 0xFFC99093),
    GREEN("Dark Green",   0xFF08150E, 0xFF0F2118, 0xFF173225, 0xFF2ECC71, 0xFF7DF0AA, 0xFFEAFFF2, 0xFF8FC9A6),
    ORANGE("Dark Orange", 0xFF1A0F06, 0xFF281809, 0xFF3A2410, 0xFFFF8A1F, 0xFFFFB366, 0xFFFFF3E8, 0xFFC9A58F),
    PINK("Dark Pink",     0xFF1A0A14, 0xFF281120, 0xFF3A172F, 0xFFFF4FB0, 0xFFFF8FCF, 0xFFFFEBF6, 0xFFC98FB3),
    CUSTOM("Custom",      0xFF0F0A1C, 0xFF181129, 0xFF21173A, 0xFF9B5CFF, 0xFFC084FC, 0xFFF1EAFF, 0xFF9D8FBF);

    public static final int BG = 0, PANEL = 1, ROW = 2, ACC = 3, ACC2 = 4, TEXT = 5, SUB = 6;
    public final String label;
    public final int[] colors;

    Theme(String label, int... colors) {
        this.label = label;
        this.colors = colors;
    }

    /** Builds a full dark palette from a single hue (0..1). */
    public static int[] custom(float h) {
        return new int[]{
                hsb(h, 0.60f, 0.11f), hsb(h, 0.55f, 0.17f), hsb(h, 0.50f, 0.25f),
                hsb(h, 0.65f, 1.00f), hsb(h, 0.45f, 1.00f), hsb(h, 0.06f, 1.00f), hsb(h, 0.20f, 0.78f)
        };
    }

    public static int hsb(float h, float s, float b) {
        h = ((h % 1f) + 1f) % 1f;
        float h6 = h * 6f;
        int i = (int) Math.floor(h6);
        float f = h6 - i, p = b * (1 - s), q = b * (1 - s * f), t = b * (1 - s * (1 - f));
        float r, g, bl;
        switch (i % 6) {
            case 0 -> { r = b; g = t; bl = p; }
            case 1 -> { r = q; g = b; bl = p; }
            case 2 -> { r = p; g = b; bl = t; }
            case 3 -> { r = p; g = q; bl = b; }
            case 4 -> { r = t; g = p; bl = b; }
            default -> { r = b; g = p; bl = q; }
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (bl * 255);
    }
}
