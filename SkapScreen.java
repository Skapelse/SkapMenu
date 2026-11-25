package com.skapclient;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SkapScreen extends Screen {

    // ================= helpers =================
    /** Animated value. Behaviour depends on the chosen animation style/speed. */
    private static final class Anim {
        float v, t, vel;
        Anim(float s) { v = t = s; }

        void tick(float dt, float speed) {
            Config.Style st = Config.style;
            if (st == Config.Style.OFF) { v = t; vel = 0; return; }
            float sp = speed * Config.speed;
            if (st == Config.Style.BOUNCY) {
                float w = sp * 0.9f, k = w * w, c = 2f * 0.42f * w;
                int steps = Math.max(1, (int) Math.ceil(dt / 0.008f));
                float h = dt / steps;
                for (int i = 0; i < steps; i++) { vel += (k * (t - v) - c * vel) * h; v += vel * h; }
                return;
            }
            if (st == Config.Style.SNAPPY) sp *= 2.2f;
            v += (t - v) * (1f - (float) Math.exp(-sp * dt));
        }
    }

    private record Entry(String id, String name, String version, String desc, String authors, String license,
                         Object cfg, String home, Path folder, ModContainer container) { }

    private record Icon(Identifier id, int w, int h) { }

    private static final Map<String, Icon> ICONS = new HashMap<>();
    private static final Set<String> NO_ICON = new HashSet<>();

    private static int alpha(int c, float a) {
        a = Math.max(0f, Math.min(1f, a));
        int al = (int) (((c >>> 24) & 255) * a);
        return (al << 24) | (c & 0xFFFFFF);
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int x = (a >>> s) & 255, y = (b >>> s) & 255;
            r |= ((int) (x + (y - x) * t) & 255) << s;
        }
        return r;
    }

    private static float ease(float t) { t = Math.max(0, Math.min(1, t)); return 1f - (float) Math.pow(1f - t, 3); }
    private static float clamp01(float f) { return Math.max(0f, Math.min(1f, f)); }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ================= state =================
    private static final int ROW_H = 30;
    private final Screen parent;
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> view = new ArrayList<>();
    private Anim[] rowHover = new Anim[0];
    private final Map<String, Anim> hovers = new HashMap<>();
    private final Anim open = new Anim(0), detail = new Anim(1), selY = new Anim(0), settingsAnim = new Anim(0);
    private final int[] pal = new int[7];
    private float scrollCur, scrollTarget;
    private int selected = 0;
    private boolean closing, settingsOpen;
    private int dragging = 0; // 1 = speed slider, 2 = hue slider
    private String query = "";
    private TextFieldWidget search;
    private long last = System.nanoTime();

    // layout
    private int px, py, pw, ph, areaY, areaH, listX, listY, listW, listH, detX, detW;
    private int btnX0, btnW, btnY, btnH = 18;
    private int gearX, gearW = 56, swBase;
    private int sliderX, sliderW, speedY, hueY, styleBtnY, resetBtnY;

    public SkapScreen(Screen parent) {
        super(Text.literal("SkapClient"));
        this.parent = parent;
        System.arraycopy(Config.colors(), 0, pal, 0, 7);
        loadEntries();
        applyFilter();
        open.t = 1;
    }

    @Override
    protected void init() {
        search = new TextFieldWidget(textRenderer, 0, 0, 100, 16, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search mods..."));
        search.setDrawsBackground(false);
        search.setMaxLength(48);
        search.setText(query);
        search.setChangedListener(s -> { query = s; applyFilter(); });
        addDrawableChild(search);
        setInitialFocus(search);
    }

    // ================= data =================
    private void loadEntries() {
        Map<String, Object> cfgs = new HashMap<>();
        try {
            for (var c : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
                try {
                    Object api = c.getEntrypoint();
                    Object f = api.getClass().getMethod("getModConfigScreenFactory").invoke(api);
                    if (f != null) cfgs.put(c.getProvider().getMetadata().getId(), f);
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }

        for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
            if (m.getContainingMod().isPresent()) continue;
            ModMetadata md = m.getMetadata();
            String id = md.getId();
            if (id.startsWith("fabric-") && !id.equals("fabric-api")) continue;
            String authors = md.getAuthors().stream().map(Person::getName).collect(Collectors.joining(", "));
            String lic = String.join(", ", md.getLicense());
            String home = md.getContact().get("homepage").orElse(md.getContact().get("sources").orElse(null));
            Path folder = null;
            try {
                Path p = m.getOrigin().getPaths().get(0);
                folder = Files.isRegularFile(p) ? p.getParent() : p;
            } catch (Throwable ignored) { }
            all.add(new Entry(id, md.getName(), md.getVersion().getFriendlyString(),
                    md.getDescription() == null || md.getDescription().isBlank() ? "No description provided." : md.getDescription(),
                    authors.isEmpty() ? "Unknown" : authors, lic.isEmpty() ? "Unspecified" : lic,
                    cfgs.get(id), home, folder, m));
        }
        all.sort(Comparator.comparing(e -> e.name().toLowerCase()));
    }

    private void applyFilter() {
        String q = query.toLowerCase().trim();
        view.clear();
        for (Entry e : all) {
            if (q.isEmpty() || e.name().toLowerCase().contains(q) || e.id().toLowerCase().contains(q)
                    || e.authors().toLowerCase().contains(q)) view.add(e);
        }
        selected = 0;
        scrollTarget = 0; scrollCur = 0;
        selY.v = selY.t = 0;
        rowHover = new Anim[view.size()];
        for (int i = 0; i < rowHover.length; i++) rowHover[i] = new Anim(0);
        detail.v = 0; detail.t = 1;
    }

    private Entry cur() { return view.isEmpty() ? null : view.get(Math.min(selected, view.size() - 1)); }

    private Icon icon(Entry e) {
        if (ICONS.containsKey(e.id())) return ICONS.get(e.id());
        if (NO_ICON.contains(e.id())) return null;
        try {
            Optional<String> p = e.container().getMetadata().getIconPath(32);
            Optional<Path> path = p.isPresent() ? e.container().findPath(p.get()) : Optional.empty();
            if (path.isPresent()) {
                try (InputStream in = Files.newInputStream(path.get())) {
                    NativeImage img = NativeImage.read(in);
                    Identifier id = Identifier.of("skapclient", "icon/" + e.id().toLowerCase().replaceAll("[^a-z0-9_./-]", "_"));
                    String nm = "skapclient_icon_" + e.id();
                    MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> nm, img));
                    Icon ic = new Icon(id, img.getWidth(), img.getHeight());
                    ICONS.put(e.id(), ic);
                    return ic;
                }
            }
        } catch (Throwable ignored) { }
        NO_ICON.add(e.id());
        return null;
    }

    // ================= layout =================
    private void layout() {
        pw = Math.min(width - 30, 620);
        ph = Math.min(height - 30, 340);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + Math.round((1f - open.v) * 18);
        areaY = py + 34; areaH = ph - 42;
        listX = px + 8; listW = 190;
        listY = areaY + 26; listH = areaH - 26;
        detX = listX + listW + 10; detW = px + pw - 8 - detX;
        btnY = areaY + areaH - btnH - 8;
        btnX0 = detX + 10;
        btnW = (detW - 20 - 12) / 3;
        gearX = px + pw - 12 - gearW;
        swBase = gearX - 8 - Theme.values().length * 20;
        sliderX = detX + 14; sliderW = detW - 28;
        speedY = areaY + 44; styleBtnY = areaY + 78; hueY = areaY + 124; resetBtnY = areaY + 148;
    }

    private float maxScroll() { return Math.max(0, view.size() * ROW_H - listH); }
    private Anim hv(String k) { return hovers.computeIfAbsent(k, x -> new Anim(0)); }

    private void text(DrawContext c, String s, int x, int y, int col) {
        if ((col >>> 24) < 8) return;
        c.drawText(textRenderer, s, x, y, col, false);
    }

    private void button(DrawContext ctx, String key, int x, int y, int w, int h, String label, boolean enabled,
                        int mx, int my, float dt, float a, int acc, int acc2, int row, int txt, int sub) {
        Anim hh = hv(key);
        hh.t = enabled && in(mx, my, x, y, w, h) ? 1 : 0;
        hh.tick(dt, 18);
        int grow = Math.round(hh.v * 2);
        int col = enabled ? lerp(alpha(acc, 0.65f), acc2, hh.v) : row;
        ctx.fill(x - grow, y - grow, x + w + grow, y + h + grow, alpha(col, a));
        text(ctx, label, x + w / 2 - textRenderer.getWidth(label) / 2, y + (h - 8) / 2, alpha(enabled ? txt : sub, a));
    }

    private void slider(DrawContext ctx, int x, int y, int w, float frac, boolean rainbow, float a, int row, int acc, int txt) {
        if (rainbow) {
            int seg = 32;
            for (int i = 0; i < seg; i++) {
                int x1 = x + w * i / seg, x2 = x + w * (i + 1) / seg;
                ctx.fill(x1, y, x2, y + 8, alpha(Theme.hsb(i / (float) seg, 0.7f, 1f), a));
            }
        } else {
            ctx.fill(x, y, x + w, y + 8, alpha(row, a));
            ctx.fill(x, y, x + Math.round(w * frac), y + 8, alpha(acc, a));
        }
        int kx = x + Math.round(w * frac);
        ctx.fill(kx - 3, y - 3, kx + 3, y + 11, alpha(txt, a));
    }

    // ================= render =================
    @Override
    public void render(DrawContext ctx, int mx, int my, float tickDelta) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1_000_000_000f);
        last = now;

        int[] target = Config.colors();
        for (int i = 0; i < 7; i++) {
            pal[i] = Config.style == Config.Style.OFF ? target[i] : lerp(pal[i], target[i], 1f - (float) Math.exp(-8 * dt));
        }

        open.tick(dt, 12);
        detail.tick(dt, 14);
        settingsAnim.t = settingsOpen ? 1 : 0;
        settingsAnim.tick(dt, 14);
        selY.t = selected * ROW_H;
        selY.tick(dt, 16);
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-14 * dt * Config.speed));
        if (Config.style == Config.Style.OFF) scrollCur = scrollTarget;

        if (closing && open.v < 0.03f) { client.setScreen(parent); return; }

        layout();
        float a = ease(open.v);
        int ACC = pal[Theme.ACC], ACC2 = pal[Theme.ACC2], TXT = pal[Theme.TEXT], SUB = pal[Theme.SUB], ROW = pal[Theme.ROW];

        ctx.fill(0, 0, width, height, alpha(0xFF000000, a * 0.55f));
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, alpha(ACC, a * 0.55f));
        ctx.fill(px, py, px + pw, py + ph, alpha(pal[Theme.BG], a));
        ctx.fillGradient(px, py, px + pw, py + 28, alpha(ACC, a * 0.28f), alpha(ACC, 0));
        text(ctx, "SKAPCLIENT", px + 10, py + 10, alpha(ACC2, a));
        text(ctx, all.size() + " mods loaded", px + 10 + textRenderer.getWidth("SKAPCLIENT") + 8, py + 10, alpha(SUB, a));

        // ---- header: theme swatches + settings button ----
        Theme[] themes = Theme.values();
        for (int i = 0; i < themes.length; i++) {
            int sx = swBase + i * 20, sy = py + 8;
            Anim h = hv("sw" + i);
            h.t = in(mx, my, sx - 2, sy - 2, 16, 16) ? 1 : 0; h.tick(dt, 18);
            int grow = Math.round(h.v * 2);
            if (themes[i] == Config.theme) ctx.fill(sx - 3 - grow, sy - 3 - grow, sx + 13 + grow, sy + 13 + grow, alpha(TXT, a * 0.9f));
            int swCol = themes[i] == Theme.CUSTOM ? Theme.custom(Config.hue)[Theme.ACC] : themes[i].colors[Theme.ACC];
            ctx.fill(sx - grow, sy - grow, sx + 10 + grow, sy + 10 + grow, alpha(swCol, a));
            if (themes[i] == Theme.CUSTOM) { // little rainbow tick so Custom is recognisable
                ctx.fill(sx - grow, sy + 8 + grow, sx + 10 + grow, sy + 10 + grow, alpha(Theme.hsb((now / 80_000_000L % 100) / 100f, 0.8f, 1f), a));
            }
            if (h.v > 0.5f) {
                String l = themes[i].label;
                text(ctx, l, swBase - textRenderer.getWidth(l) - 6, py + 10, alpha(SUB, a * h.v));
            }
        }
        button(ctx, "gear", gearX, py + 6, gearW, 16, settingsOpen ? "Back" : "Settings", true, mx, my, dt, a,
                ACC, ACC2, ROW, TXT, SUB);

        // ---- search box ----
        ctx.fill(listX, areaY, listX + listW, areaY + 22, alpha(pal[Theme.PANEL], a));
        ctx.fill(listX, areaY + 21, listX + listW, areaY + 22, alpha(ACC, a * (search.isFocused() ? 0.9f : 0.3f)));
        search.setX(listX + 6); search.setY(areaY + 6); search.setWidth(listW - 12);
        search.setEditableColor(0xFF000000 | (TXT & 0xFFFFFF));
        search.visible = a > 0.6f;
        search.render(ctx, mx, my, tickDelta);

        // ---- list ----
        ctx.fill(listX, listY, listX + listW, listY + listH, alpha(pal[Theme.PANEL], a));
        ctx.enableScissor(listX, listY, listX + listW, listY + listH);
        if (!view.isEmpty()) {
            int hy = listY + Math.round(selY.v - scrollCur);
            ctx.fill(listX, hy, listX + listW, hy + ROW_H, alpha(ACC, a * 0.22f));
            ctx.fill(listX, hy, listX + 3, hy + ROW_H, alpha(ACC, a));
        } else {
            text(ctx, "No mods found", listX + 10, listY + 10, alpha(SUB, a));
        }
        for (int i = 0; i < view.size(); i++) {
            int ry = listY + i * ROW_H - Math.round(scrollCur);
            if (ry + ROW_H < listY || ry > listY + listH) continue;
            Anim h = rowHover[i];
            h.t = in(mx, my, listX, listY, listW, listH) && in(mx, my, listX, ry, listW, ROW_H) ? 1 : 0;
            h.tick(dt, 18);
            ctx.fill(listX, ry, listX + listW, ry + ROW_H, alpha(ROW, a * clamp01(h.v) * 0.8f));
            int shift = Math.round(h.v * 3);
            Entry e = view.get(i);
            drawAvatar(ctx, e, listX + 8 + shift, ry + 5, 20, lerp(ROW, ACC, 0.5f + 0.5f * h.v), a, TXT);
            text(ctx, textRenderer.trimToWidth(e.name(), listW - 50), listX + 34 + shift, ry + 6, alpha(TXT, a));
            text(ctx, textRenderer.trimToWidth("v" + e.version(), listW - 50), listX + 34 + shift, ry + 17, alpha(SUB, a));
        }
        ctx.disableScissor();
        if (maxScroll() > 0) {
            int bh = Math.max(14, (int) (listH * (listH / (float) (view.size() * ROW_H))));
            int by = listY + (int) ((listH - bh) * (scrollCur / maxScroll()));
            ctx.fill(listX + listW - 3, by, listX + listW - 1, by + bh, alpha(ACC, a * 0.7f));
        }

        // ---- right side: detail / settings (cross-fade) ----
        ctx.fill(detX, areaY, detX + detW, areaY + areaH, alpha(pal[Theme.PANEL], a));
        float sFrac = ease(settingsAnim.v);
        if (sFrac < 0.98f) renderDetail(ctx, mx, my, dt, a * (1f - sFrac));
        if (sFrac > 0.02f) renderSettings(ctx, mx, my, dt, a * sFrac);
    }

    private void drawAvatar(DrawContext ctx, Entry e, int x, int y, int size, int bg, float a, int txt) {
        Icon ic = icon(e);
        if (ic != null) {
            ctx.fill(x, y, x + size, y + size, alpha(0xFF000000, a * 0.35f));
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ic.id(), x, y, 0f, 0f, size, size,
                    ic.w(), ic.h(), ic.w(), ic.h(), alpha(0xFFFFFFFF, a));
        } else {
            ctx.fill(x, y, x + size, y + size, alpha(bg, a));
            String l = e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase();
            text(ctx, l, x + size / 2 - textRenderer.getWidth(l) / 2, y + size / 2 - 4, alpha(txt, a));
        }
    }

    private void renderDetail(DrawContext ctx, int mx, int my, float dt, float a) {
        Entry e = cur();
        if (e == null) return;
        int ACC = pal[Theme.ACC], ACC2 = pal[Theme.ACC2], TXT = pal[Theme.TEXT], SUB = pal[Theme.SUB], ROW = pal[Theme.ROW];
        float d = ease(detail.v);
        float da = a * d;
        int dx = detX + 10 + Math.round((1f - d) * 10), dy = areaY + 10;

        drawAvatar(ctx, e, dx, dy, 34, ACC, da, TXT);
        text(ctx, textRenderer.trimToWidth(e.name(), detW - 70), dx + 44, dy + 3, alpha(TXT, da));
        text(ctx, textRenderer.trimToWidth(e.id() + "  ·  v" + e.version(), detW - 70), dx + 44, dy + 16, alpha(ACC2, da));
        text(ctx, textRenderer.trimToWidth("Authors: " + e.authors(), detW - 24), dx, dy + 44, alpha(SUB, da));
        text(ctx, textRenderer.trimToWidth("License: " + e.license(), detW - 24), dx, dy + 56, alpha(SUB, da));
        ctx.fill(dx, dy + 69, dx + detW - 20, dy + 70, alpha(ACC, da * 0.5f));

        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(e.desc()), detW - 24);
        int ly = dy + 77, maxY = btnY - 10;
        for (OrderedText l : lines) {
            if (ly + 9 > maxY) break;
            if (da * 255 >= 8) ctx.drawText(textRenderer, l, dx, ly, alpha(TXT, da), false);
            ly += 11;
        }

        button(ctx, "b0", btnX0, btnY, btnW, btnH, e.cfg() != null ? "Settings" : "No settings", e.cfg() != null,
                mx, my, dt, da, ACC, ACC2, ROW, TXT, SUB);
        button(ctx, "b1", btnX0 + btnW + 6, btnY, btnW, btnH, "Website", e.home() != null,
                mx, my, dt, da, ACC, ACC2, ROW, TXT, SUB);
        button(ctx, "b2", btnX0 + 2 * (btnW + 6), btnY, btnW, btnH, "Open Folder", e.folder() != null,
                mx, my, dt, da, ACC, ACC2, ROW, TXT, SUB);
    }

    private void renderSettings(DrawContext ctx, int mx, int my, float dt, float a) {
        int ACC = pal[Theme.ACC], ACC2 = pal[Theme.ACC2], TXT = pal[Theme.TEXT], SUB = pal[Theme.SUB], ROW = pal[Theme.ROW];
        int x = sliderX;
        text(ctx, "SKAPCLIENT SETTINGS", x, areaY + 12, alpha(ACC2, a));
        ctx.fill(x, areaY + 26, x + sliderW, areaY + 27, alpha(ACC, a * 0.5f));

        text(ctx, String.format("Animation speed: %.1fx", Config.speed), x, speedY - 14, alpha(TXT, a));
        slider(ctx, x, speedY, sliderW, (Config.speed - 0.5f) / 1.5f, false, a, ROW, ACC, TXT);

        text(ctx, "Animation style", x, styleBtnY - 14, alpha(TXT, a));
        button(ctx, "style", x, styleBtnY, 140, 18, Config.style.label + "  (click to change)", true,
                mx, my, dt, a, ACC, ACC2, ROW, TXT, SUB);

        text(ctx, "Custom colour (drag to pick a hue)", x, hueY - 14, alpha(TXT, a));
        slider(ctx, x, hueY, sliderW, Config.hue, true, a, ROW, ACC, TXT);

        button(ctx, "reset", x, resetBtnY, 140, 18, "Reset to defaults", true, mx, my, dt, a, ACC, ACC2, ROW, TXT, SUB);
    }

    // ================= input =================
    private void setSlider(int which, double mx) {
        float f = clamp01((float) ((mx - sliderX) / sliderW));
        if (which == 1) Config.speed = 0.5f + f * 1.5f;
        else { Config.hue = f; Config.theme = Theme.CUSTOM; }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() != 0) return super.mouseClicked(click, doubled);

        if (in(mx, my, gearX, py + 6, gearW, 16)) { settingsOpen = !settingsOpen; return true; }

        Theme[] themes = Theme.values();
        for (int i = 0; i < themes.length; i++) {
            if (in(mx, my, swBase + i * 20 - 2, py + 6, 16, 16)) {
                Config.theme = themes[i];
                if (themes[i] == Theme.CUSTOM) settingsOpen = true; // jump to the colour slider
                Config.save();
                return true;
            }
        }

        if (in(mx, my, listX, areaY, listW, 22)) return super.mouseClicked(click, doubled); // search box

        if (in(mx, my, listX, listY, listW, listH)) {
            int idx = (int) ((my - listY + scrollCur) / ROW_H);
            if (idx >= 0 && idx < view.size() && idx != selected) {
                selected = idx;
                detail.v = 0; detail.t = 1;
            }
            return true;
        }

        if (settingsOpen && settingsAnim.v > 0.5f) {
            if (in(mx, my, sliderX - 4, speedY - 6, sliderW + 8, 20)) { dragging = 1; setSlider(1, mx); return true; }
            if (in(mx, my, sliderX - 4, hueY - 6, sliderW + 8, 20)) { dragging = 2; setSlider(2, mx); return true; }
            if (in(mx, my, sliderX, styleBtnY, 140, 18)) { Config.style = Config.style.next(); Config.save(); return true; }
            if (in(mx, my, sliderX, resetBtnY, 140, 18)) { Config.reset(); Config.save(); return true; }
            return true;
        }

        Entry e = cur();
        if (e != null && !settingsOpen) {
            if (in(mx, my, btnX0, btnY, btnW, btnH) && e.cfg() != null) {
                try {
                    Class<?> iface = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory");
                    client.setScreen((Screen) iface.getMethod("create", Screen.class).invoke(e.cfg(), this));
                } catch (Throwable t) { t.printStackTrace(); }
                return true;
            }
            if (in(mx, my, btnX0 + btnW + 6, btnY, btnW, btnH) && e.home() != null) {
                ConfirmLinkScreen.open(this, e.home());
                return true;
            }
            if (in(mx, my, btnX0 + 2 * (btnW + 6), btnY, btnW, btnH) && e.folder() != null) {
                try { Util.getOperatingSystem().open(e.folder().toUri()); } catch (Throwable t) { t.printStackTrace(); }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging != 0) { setSlider(dragging, click.x()); return true; }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != 0) { dragging = 0; Config.save(); return true; }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scrollTarget -= (float) v * 34f;
        return true;
    }

    @Override
    public void close() {
        if (!closing) { closing = true; open.t = 0; }
    }

    @Override
    public boolean shouldPause() { return false; }
}
