package dev.crystalclient.gui;

import dev.crystalclient.core.Config;
import dev.crystalclient.core.HudModule;
import dev.crystalclient.core.Module;
import dev.crystalclient.core.ModuleManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Drag to move, scroll to resize, right-click to show/hide. */
public class HudEditorScreen extends Screen {
    private HudModule drag, hover;
    private double offX, offY;

    public HudEditorScreen() { super(Text.literal("HUD Editor")); }
    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Config.saveCurrent(); }

    private HudModule at(double mx, double my) {
        HudModule found = null;
        for (Module m : ModuleManager.all()) if (m instanceof HudModule h && h.contains(mx, my)) found = h;
        return found;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x80000000);
        ctx.drawCenteredTextWithShadow(textRenderer, "HUD Editor  -  drag: move   scroll: resize   right-click: show/hide   Esc: done", width / 2, 8, 0xFFFFFFFF);
        hover = at(mx, my);
        for (Module m : ModuleManager.all()) {
            if (!(m instanceof HudModule h)) continue;
            h.draw(ctx, true);
            int x = (int) h.x.d(), y = (int) h.y.d(), w = (int) (h.width() * h.scale.d()), hh = (int) (h.height() * h.scale.d());
            int c = h == hover || h == drag ? 0xFFFFE066 : h.isEnabled() ? 0xFF55FF99 : 0xFF666677;
            ctx.fill(x, y, x + w, y + 1, c); ctx.fill(x, y + hh, x + w, y + hh + 1, c);
            ctx.fill(x, y, x + 1, y + hh, c); ctx.fill(x + w, y, x + w + 1, y + hh + 1, c);
            ctx.drawText(textRenderer, h.name + (h.isEnabled() ? "" : " (hidden)"), x, y - 10, c, true);
        }
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        HudModule h = at(click.x(), click.y());
        if (h != null) {
            if (click.button() == 1) h.toggle();
            else { drag = h; offX = click.x() - h.x.d(); offY = click.y() - h.y.d(); }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (drag != null) {
            drag.x.set(Math.max(0, Math.min(width - 10, click.x() - offX)));
            drag.y.set(Math.max(0, Math.min(height - 10, click.y() - offY)));
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override public boolean mouseReleased(Click click) { drag = null; return super.mouseReleased(click); }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        HudModule t = drag != null ? drag : hover;
        if (t == null) return false;
        t.scale.set(t.scale.d() + v * 0.1);
        return true;
    }
}
