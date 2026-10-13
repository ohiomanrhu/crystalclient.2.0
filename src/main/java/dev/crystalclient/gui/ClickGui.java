package dev.crystalclient.gui;

import dev.crystalclient.core.*;
import dev.crystalclient.core.Module;
import dev.crystalclient.setting.Setting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.*;

/** Category tabs, searchable module list with expandable settings, profile bar and HUD-editor shortcut. */
public class ClickGui extends Screen {
    private static final int W = 520, H = 310, KEY_ESC = 256, KEY_BACKSPACE = 259, KEY_DELETE = 261;

    private record Row(int x, int y, int w, int h, Object obj, boolean list, String desc) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private Category cat = Category.COMBAT;
    private String search = "";
    private boolean searchFocus;
    private int scroll, contentHeight, listTop, listBottom;
    private final Set<Module> expanded = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private Row dragRow;
    private Setting.Num dragging;
    private Setting.Key listening;
    private List<String> profiles = new ArrayList<>();
    private int profileIdx;
    private String status = "";

    public ClickGui() { super(Text.literal("CrystalClient")); refreshProfiles(); }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Config.saveCurrent(); }

    private void refreshProfiles() {
        profiles = Config.listProfiles();
        if (profiles.isEmpty()) profiles.add("default");
        profileIdx = Math.max(0, Math.min(profileIdx, profiles.size() - 1));
    }
    private String profile() { return profiles.get(profileIdx); }

    private boolean matches(Module m) {
        String q = search.toLowerCase();
        if (m.name.toLowerCase().contains(q)) return true;
        for (Setting<?> s : m.settings) if (s.name.toLowerCase().contains(q)) return true;
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        rows.clear();
        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        ctx.fill(0, 0, width, height, 0x60000000);
        ctx.fill(x0, y0, x0 + W, y0 + H, 0xF0141418);
        ctx.fill(x0, y0, x0 + W, y0 + 22, 0xFF1E1E26);
        ctx.drawText(textRenderer, "CrystalClient  1.21.11", x0 + 8, y0 + 7, 0xFF7AE0FF, true);

        // search box
        int sx = x0 + W - 190, sy = y0 + 4;
        ctx.fill(sx, sy, sx + 182, sy + 14, searchFocus ? 0xFF2C2C3A : 0xFF22222C);
        boolean blink = searchFocus && (System.currentTimeMillis() / 500 % 2 == 0);
        ctx.drawText(textRenderer, search.isEmpty() && !searchFocus ? "Search modules & settings..." : search + (blink ? "_" : ""),
                sx + 4, sy + 3, search.isEmpty() ? 0xFF808090 : 0xFFFFFFFF, false);
        rows.add(new Row(sx, sy, 182, 14, "search", false, "Type to search every module and setting"));

        // category tabs
        int cy = y0 + 30;
        for (Category c : Category.values()) {
            final Category cc = c;
            button(ctx, x0 + 6, cy, 76, 18, c.label, c == cat && search.isEmpty(), () -> { cat = cc; scroll = 0; search = ""; }, "Show " + c.label + " modules");
            cy += 22;
        }

        // module list (scissored)
        int lx = x0 + 90, lw = W - 98;
        listTop = y0 + 28; listBottom = y0 + H - 44;
        ctx.enableScissor(lx, listTop, lx + lw, listBottom);
        int y = listTop - scroll;
        for (Module m : ModuleManager.all()) {
            boolean show = search.isEmpty() ? m.category == cat : matches(m);
            if (!show) continue;
            boolean open = expanded.contains(m) || (!search.isEmpty() && m.settings.stream().anyMatch(s -> s.name.toLowerCase().contains(search.toLowerCase())) && !m.name.toLowerCase().contains(search.toLowerCase()));
            // module header
            int hc = m.isEnabled() ? 0xFF2A5A4A : 0xFF22222C;
            ctx.fill(lx, y, lx + lw, y + 18, hc);
            ctx.fill(lx, y, lx + 3, y + 18, m.isEnabled() ? 0xFF55FF99 : 0xFF444455);
            ctx.drawText(textRenderer, m.name, lx + 8, y + 5, 0xFFFFFFFF, false);
            String tag = (m.isEnabled() ? "ON" : "OFF") + "  T" + m.level() + (open ? "  v" : "  >");
            ctx.drawText(textRenderer, tag, lx + lw - textRenderer.getWidth(tag) - 6, y + 5, m.isEnabled() ? 0xFF55FF99 : 0xFF909098, false);
            addRow(new Row(lx, y, lw, 18, m, true, m.description + "  [left: toggle, right: settings]"));
            y += 20;
            if (open) {
                for (Setting<?> s : m.settings) {
                    if (!search.isEmpty() && !m.name.toLowerCase().contains(search.toLowerCase()) && !s.name.toLowerCase().contains(search.toLowerCase())) continue;
                    int sxx = lx + 8, sw = lw - 8;
                    ctx.fill(sxx, y, sxx + sw, y + 14, 0xFF1A1A22);
                    if (s instanceof Setting.Num n) ctx.fill(sxx, y, sxx + (int) (sw * n.fraction()), y + 14, 0xFF2F4F66);
                    ctx.drawText(textRenderer, s.name, sxx + 4, y + 3, 0xFFD0D0D8, false);
                    String v = (listening == s) ? "press a key..." : s.display();
                    int vc = s instanceof Setting.Bool b ? (b.get() ? 0xFF55FF99 : 0xFFFF7777) : 0xFFFFFFFF;
                    ctx.drawText(textRenderer, v, sxx + sw - textRenderer.getWidth(v) - 4, y + 3, vc, false);
                    addRow(new Row(sxx, y, sw, 14, s, true, s.description));
                    y += 15;
                }
                y += 3;
            }
        }
        ctx.disableScissor();
        contentHeight = y + scroll - listTop;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (listBottom - listTop))));

        // footer: presets + profiles
        int fy = y0 + H - 38, fx = x0 + 6;
        button(ctx, fx, fy, 70, 16, "Crystal PvP", false, () -> loadNamed(Config.CRYSTAL), "Load the Crystal PvP preset"); fx += 74;
        button(ctx, fx, fy, 70, 16, "Anchor PvP", false, () -> loadNamed(Config.ANCHOR), "Load the Anchor PvP preset"); fx += 78;
        button(ctx, fx, fy, 16, 16, "<", false, () -> { profileIdx = Math.floorMod(profileIdx - 1, profiles.size()); }, "Previous profile"); fx += 18;
        button(ctx, fx, fy, 100, 16, profile(), false, () -> {}, "Selected profile"); fx += 102;
        button(ctx, fx, fy, 16, 16, ">", false, () -> { profileIdx = (profileIdx + 1) % profiles.size(); }, "Next profile"); fx += 20;
        button(ctx, fx, fy, 38, 16, "Save", false, () -> { Config.saveProfile(profile()); refreshProfiles(); status = "Saved " + profile(); }, "Overwrite the selected profile with current settings"); fx += 40;
        button(ctx, fx, fy, 38, 16, "Load", false, () -> loadNamed(profile()), "Load the selected profile"); fx += 40;
        button(ctx, fx, fy, 34, 16, "New", false, () -> {
            String n = "profile-" + (profiles.size() + 1);
            Config.saveProfile(n); refreshProfiles(); profileIdx = profiles.indexOf(n); status = "Created " + n;
        }, "Save current settings as a new profile"); fx += 36;
        button(ctx, fx, fy, 40, 16, "HUD", false, () -> client.setScreen(new HudEditorScreen()), "Open the HUD editor");

        // status / hover description
        String hover = status;
        for (int i = rows.size() - 1; i >= 0; i--) {
            Row r = rows.get(i);
            if (r.hit(mx, my) && (!r.list || (my >= listTop && my < listBottom))) { hover = r.desc; break; }
        }
        ctx.drawText(textRenderer, hover, x0 + 8, y0 + H - 14, 0xFF9090A8, false);
        super.render(ctx, mx, my, delta);
    }

    private void loadNamed(String n) { status = Config.loadProfile(n) ? "Loaded " + n : "No such profile: " + n; }

    private void addRow(Row r) { if (r.y + r.h > listTop && r.y < listBottom) rows.add(r); }

    private void button(DrawContext ctx, int x, int y, int w, int h, String label, boolean active, Runnable r, String desc) {
        ctx.fill(x, y, x + w, y + h, active ? 0xFF2F5F7A : 0xFF26262F);
        ctx.drawText(textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
        rows.add(new Row(x, y, w, h, r, false, desc));
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int b = click.button();
        searchFocus = false;
        if (listening != null) listening = null;
        for (int i = rows.size() - 1; i >= 0; i--) {
            Row r = rows.get(i);
            if (!r.hit(mx, my) || (r.list && (my < listTop || my >= listBottom))) continue;
            Object o = r.obj;
            if (o instanceof String s && s.equals("search")) searchFocus = true;
            else if (o instanceof Runnable run) run.run();
            else if (o instanceof Module m) { if (b == 0) m.toggle(); else if (!expanded.remove(m)) expanded.add(m); }
            else if (o instanceof Setting.Bool s) s.toggle();
            else if (o instanceof Setting.Num n) { dragging = n; dragRow = r; slide(mx); }
            else if (o instanceof Setting.Choice c) c.cycle(b == 1 ? -1 : 1);
            else if (o instanceof Setting.Key k) { if (b == 1) k.set(-1); else listening = k; }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void slide(double mx) { if (dragging != null) dragging.setFraction((mx - dragRow.x) / dragRow.w); }

    @Override public boolean mouseDragged(Click click, double dx, double dy) { if (dragging != null) { slide(click.x()); return true; } return super.mouseDragged(click, dx, dy); }
    @Override public boolean mouseReleased(Click click) { dragging = null; return super.mouseReleased(click); }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, Math.min(scroll - (int) (v * 16), Math.max(0, contentHeight - (listBottom - listTop))));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (listening != null) {
            if (key == KEY_BACKSPACE || key == KEY_DELETE) listening.set(-1);
            else if (key != KEY_ESC) listening.set(key);
            listening = null;
            return true;
        }
        if (searchFocus) {
            if (key == KEY_BACKSPACE && !search.isEmpty()) { search = search.substring(0, search.length() - 1); scroll = 0; return true; }
            if (key == KEY_ESC) { searchFocus = false; return true; }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchFocus) {
            int cp = input.codepoint();
            if (cp >= 32 && cp != 127) { search += new String(Character.toChars(cp)); scroll = 0; }
            return true;
        }
        return super.charTyped(input);
    }
}
