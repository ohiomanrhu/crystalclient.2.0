package dev.crystalclient.modules;

import dev.crystalclient.core.HudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

public class TargetHud extends HudModule {
    public TargetHud() { super("Target HUD", "Target health, armour, distance and damage estimate", 10, 60); }

    @Override public int width() { return 150; }
    @Override public int height() { return 56; }

    @Override
    protected void render(DrawContext ctx, boolean editor) {
        PlayerEntity t = TargetSelector.get();
        if (t == null && editor) t = mc.player;     // sample in the HUD editor
        if (t == null) return;
        var tr = mc.textRenderer;
        ctx.fill(0, 0, width(), height(), 0xB0101018);
        ctx.fill(0, 0, 2, height(), 0xFF7AE0FF);
        ctx.drawText(tr, t.getName().getString(), 6, 4, 0xFFFFFFFF, true);

        float hp = t.getHealth(), abs = t.getAbsorptionAmount();
        float frac = Math.max(0f, Math.min(1f, hp / 20f));
        int color = frac > 0.6f ? 0xFF55FF55 : frac > 0.3f ? 0xFFFFD955 : 0xFFFF5555;
        ctx.fill(6, 16, 6 + 138, 22, 0xFF303040);
        ctx.fill(6, 16, 6 + (int) (138 * frac), 22, color);
        if (abs > 0) ctx.fill(6, 22, 6 + (int) (138 * Math.min(1f, abs / 20f)), 24, 0xFFFFD700);
        ctx.drawText(tr, String.format("%.1f HP", hp + abs), 6, 27, 0xFFFFFFFF, false);
        double dist = mc.player == null ? 0 : mc.player.distanceTo(t);
        ctx.drawText(tr, String.format("%.1fm", dist), 66, 27, 0xFFB0B0C0, false);
        if (t.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) ctx.drawText(tr, "TOTEM", 104, 27, 0xFFFFD955, false);

        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) ctx.drawItem(t.getEquippedStack(slots[i]), 6 + i * 18, 38);

        var ca = dev.crystalclient.core.ModuleManager.get(CrystalAssist.class);
        if (ca.isEnabled())
            ctx.drawText(tr, String.format("%.0f/%.0f", ca.lastTargetDmg, ca.lastSelfDmg), 82, 43, 0xFFFF9090, false);
    }
}
