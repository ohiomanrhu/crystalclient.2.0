package dev.crystalclient.modules;

import dev.crystalclient.core.HudModule;
import dev.crystalclient.core.Module;
import dev.crystalclient.core.ModuleManager;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class ModuleListHud extends HudModule {
    public ModuleListHud() { super("Active Modules", "Lists enabled modules and warnings", 10, 10); }

    private List<String> lines(boolean editor) {
        List<String> out = new ArrayList<>();
        for (Module m : ModuleManager.all())
            if (m.isEnabled() && !(m instanceof HudModule) && m != CoreSettings.I) out.add(m.name);
        if (AutoTotem.I != null && AutoTotem.I.isEnabled() && AutoTotem.I.noTotems) out.add("! NO TOTEMS !");
        if (out.isEmpty() && editor) out.add("(no active modules)");
        return out;
    }

    @Override public int width() {
        int w = 90;
        for (String s : lines(true)) w = Math.max(w, mc.textRenderer.getWidth(s) + 10);
        return w;
    }
    @Override public int height() { return Math.max(12, lines(true).size() * 11 + 3); }

    @Override
    protected void render(DrawContext ctx, boolean editor) {
        int y = 2;
        for (String s : lines(editor)) {
            boolean warn = s.startsWith("!");
            ctx.fill(0, y - 1, mc.textRenderer.getWidth(s) + 8, y + 10, 0x90101018);
            ctx.fill(0, y - 1, 2, y + 10, warn ? 0xFFFF5555 : 0xFF55FF99);
            ctx.drawText(mc.textRenderer, s, 5, y + 1, warn ? 0xFFFF5555 : 0xFFFFFFFF, true);
            y += 11;
        }
    }
}
