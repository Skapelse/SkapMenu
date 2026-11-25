package com.skapclient;

import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Config {
    public enum Style {
        SMOOTH("Smooth"), BOUNCY("Bouncy"), SNAPPY("Snappy"), OFF("Off");
        public final String label;
        Style(String l) { label = l; }
        public Style next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static Theme theme = Theme.PURPLE; // default: Dark Purple
    public static Style style = Style.SMOOTH;
    public static float speed = 1.0f;         // animation speed multiplier 0.5 - 2.0
    public static float hue = 0.75f;          // custom theme hue 0 - 1

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("skapclient.properties"); }

    public static int[] colors() { return theme == Theme.CUSTOM ? Theme.custom(hue) : theme.colors; }

    public static void reset() { theme = Theme.PURPLE; style = Style.SMOOTH; speed = 1f; hue = 0.75f; }

    public static void load() {
        try {
            if (!Files.exists(file())) return;
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file())) { p.load(r); }
            theme = Theme.valueOf(p.getProperty("theme", "PURPLE"));
            style = Style.valueOf(p.getProperty("style", "SMOOTH"));
            speed = Math.max(0.5f, Math.min(2f, Float.parseFloat(p.getProperty("speed", "1"))));
            hue = Math.max(0f, Math.min(1f, Float.parseFloat(p.getProperty("hue", "0.75"))));
        } catch (Exception ignored) { reset(); }
    }

    public static void save() {
        try {
            Properties p = new Properties();
            p.setProperty("theme", theme.name());
            p.setProperty("style", style.name());
            p.setProperty("speed", Float.toString(speed));
            p.setProperty("hue", Float.toString(hue));
            try (Writer w = Files.newBufferedWriter(file())) { p.store(w, "SkapClient settings"); }
        } catch (Exception ignored) { }
    }
}
