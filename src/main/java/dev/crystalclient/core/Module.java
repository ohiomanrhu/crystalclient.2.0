package dev.crystalclient.core;

import dev.crystalclient.CrystalClient;
import dev.crystalclient.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

import java.util.*;

/** Base class for every feature. Priority levels: 0 Survival, 1 Defensive, 2 Combat, 3 Utility, 4 Cosmetic. */
public abstract class Module {
    public static final String[] TIERS = {"Survival", "Defensive", "Combat", "Utility", "Cosmetic"};
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    public final String name, description;
    public final Category category;
    public final List<Setting<?>> settings = new ArrayList<>();
    public final Setting.Key toggleKey = new Setting.Key("Toggle key", "Key that toggles this module", -1);
    private final Setting.Choice priority;   // null when the tier is fixed
    private final int fixedLevel;
    private boolean enabled;
    private int errors;
    private final Map<Setting.Key, Boolean> keyState = new IdentityHashMap<>();

    /** @param tier default priority tier; @param configurable whether the user may change it */
    protected Module(String name, String description, Category cat, int tier, boolean configurable) {
        this.name = name; this.description = description; this.category = cat; this.fixedLevel = tier;
        settings.add(toggleKey);
        if (configurable) {
            priority = new Setting.Choice("Priority tier", "Lower tier wins inventory conflicts", TIERS[tier], TIERS);
            settings.add(priority);
        } else priority = null;
    }

    public int level() { return priority == null ? fixedLevel : priority.index(); }
    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on; errors = 0;
        try { if (on) onEnable(); else onDisable(); }
        catch (Throwable t) { CrystalClient.LOG.error("{} toggle failed", name, t); }
        CrystalClient.debug("{} {}", name, on ? "enabled" : "disabled");
    }
    public void toggle() { setEnabled(!enabled); }

    protected void onEnable() {}
    protected void onDisable() {}
    protected void onTick(MinecraftClient mc) {}

    /** Runs the tick; a module that throws 5 times in a row disables itself instead of crashing the client. */
    public final void safeTick(MinecraftClient mc) {
        try { onTick(mc); errors = 0; }
        catch (Throwable t) {
            CrystalClient.LOG.error("{} threw during tick ({}/5)", name, errors + 1, t);
            if (++errors >= 5) { CrystalClient.LOG.error("{} auto-disabled after repeated errors", name); setEnabled(false); }
        }
    }

    /** Edge-triggered key poll. */
    public boolean pressed(Setting.Key k) {
        int code = k.get();
        if (code < 0 || mc.currentScreen != null) { keyState.put(k, false); return false; }
        boolean down = InputUtil.isKeyPressed(mc.getWindow(), code);
        boolean was = keyState.getOrDefault(k, false);
        keyState.put(k, down);
        return down && !was;
    }

    // ---- setting helpers ----
    protected Setting.Bool bool(String n, String d, boolean def) { var s = new Setting.Bool(n, d, def); settings.add(s); return s; }
    protected Setting.Num num(String n, String d, double def, double min, double max, double step) { var s = new Setting.Num(n, d, def, min, max, step); settings.add(s); return s; }
    protected Setting.Choice choice(String n, String d, String def, String... o) { var s = new Setting.Choice(n, d, def, o); settings.add(s); return s; }
    protected Setting.Key key(String n, String d) { var s = new Setting.Key(n, d, -1); settings.add(s); return s; }
}
