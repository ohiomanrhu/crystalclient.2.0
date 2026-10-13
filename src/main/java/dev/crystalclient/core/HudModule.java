package dev.crystalclient.core;

import dev.crystalclient.setting.Setting;
import net.minecraft.client.gui.DrawContext;

public abstract class HudModule extends Module {
    public final Setting.Num x, y, scale;

    protected HudModule(String name, String desc, int defX, int defY) {
        super(name, desc, Category.HUD, 4, false);
        x = num("X", "Horizontal position", defX, 0, 4000, 1);
        y = num("Y", "Vertical position", defY, 0, 4000, 1);
        scale = num("Scale", "Element size", 1.0, 0.5, 3.0, 0.1);
    }

    public abstract int width();
    public abstract int height();
    protected abstract void render(DrawContext ctx, boolean editor);

    public void draw(DrawContext ctx, boolean editor) {
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x.f(), y.f());
        m.scale(scale.f(), scale.f());
        render(ctx, editor);
        m.popMatrix();
    }

    public boolean contains(double mx, double my) {
        return mx >= x.d() && my >= y.d() && mx <= x.d() + width() * scale.d() && my <= y.d() + height() * scale.d();
    }
}
