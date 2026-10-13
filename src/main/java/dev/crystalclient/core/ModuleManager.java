package dev.crystalclient.core;

import dev.crystalclient.gui.HudEditorScreen;
import dev.crystalclient.modules.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.*;

public final class ModuleManager {
    private static final List<Module> modules = new ArrayList<>();

    private ModuleManager() {}

    public static void init() {
        add(new CoreSettings());
        add(new AutoTotem());
        add(new AutoSwap());
        add(new CrystalAssist());
        add(new AutoAnchor());
        add(new RailHelper());
        add(new TargetHud());
        add(new ModuleListHud());
        get(CoreSettings.class).setEnabled(true);
    }

    private static void add(Module m) { modules.add(m); }
    public static List<Module> all() { return modules; }

    public static <T extends Module> T get(Class<T> c) {
        for (Module m : modules) if (c.isInstance(m)) return c.cast(m);
        throw new IllegalStateException("module not registered: " + c);
    }

    public static void tick(MinecraftClient mc) {
        PriorityManager.tick();
        if (mc.player == null || mc.world == null) { PriorityManager.reset(); return; }
        for (Module m : modules) if (m.pressed(m.toggleKey)) m.toggle();
        TargetSelector.update(mc);
        // Survival-tier modules run first so they claim the inventory before anything else.
        List<Module> active = new ArrayList<>();
        for (Module m : modules) if (m.isEnabled()) active.add(m);
        active.sort(Comparator.comparingInt(Module::level));
        for (Module m : active) m.safeTick(mc);
    }

    public static void renderHud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null || mc.currentScreen instanceof HudEditorScreen) return;
        for (Module m : modules)
            if (m instanceof HudModule h && h.isEnabled()) h.draw(ctx, false);
    }
}
