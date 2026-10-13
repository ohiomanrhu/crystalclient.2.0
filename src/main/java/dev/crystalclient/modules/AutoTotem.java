package dev.crystalclient.modules;

import dev.crystalclient.CrystalClient;
import dev.crystalclient.core.*;
import dev.crystalclient.core.Module;
import dev.crystalclient.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class AutoTotem extends Module {
    public static AutoTotem I;
    private static boolean isTotem(ItemStack s) { return s.isOf(Items.TOTEM_OF_UNDYING); }

    private final Setting.Choice mode = choice("Mode", "Always = keep a totem in the offhand; Danger = only when threatened", "Danger", "Danger", "Always");
    private final Setting.Num health = num("Health threshold", "Equip at/below this HP (2 HP = 1 heart)", 10, 1, 20, 0.5);
    private final Setting.Bool danger = bool("Danger detection", "Predict end crystal and fall damage", true);
    private final Setting.Num radius = num("Danger radius", "Crystal scan radius", 12, 4, 20, 1);
    private final Setting.Num margin = num("Danger margin", "Equip if predicted damage leaves less than this HP", 2, 0, 10, 0.5);
    private final Setting.Bool useInv = bool("Search inventory", "Also take totems from the main inventory", true);
    private final Setting.Num delay = num("Swap delay (ticks)", "Minimum ticks between swap attempts", 0, 0, 10, 1);
    private final Setting.Bool restore = bool("Restore offhand", "Put the previous offhand item back once safe (Danger mode only)", false);
    private final Setting.Num restoreDelay = num("Restore delay (ticks)", "Safe time required before restoring", 60, 10, 400, 10);
    private final Setting.Bool warn = bool("Warn when out of totems", "Log/alert when no totem is available", true);

    private long lastAction = -100, verifyUntil, coolUntil, lastWarn = -1000, safeSince = -1;
    private boolean pending;
    private int fails;
    private Item prevItem;
    private boolean busy;
    public boolean noTotems;

    public AutoTotem() { super("Auto Totem", "Keeps a Totem of Undying in the offhand", Category.COMBAT, 0, false); I = this; }

    /** True while this module is working on the offhand; combat modules must hold off. */
    public static boolean busy() { return I != null && I.isEnabled() && I.busy; }

    @Override protected void onDisable() { busy = false; noTotems = false; pending = false; }

    @Override
    protected void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        long now = PriorityManager.now();
        boolean offTotem = isTotem(p.getOffHandStack());
        boolean threatened = threatened(p);
        int slot = InventoryUtil.find(AutoTotem::isTotem, useInv.get());
        noTotems = !offTotem && slot < 0;

        if (offTotem) {                       // goal reached
            pending = false; fails = 0; busy = false;
            handleRestore(p, threatened, now);
            return;
        }
        boolean want = mode.is("Always") || threatened;
        busy = false;
        safeSince = -1;
        if (!want) return;

        PriorityManager.acquire(this, level(), 2);   // survival outranks everything else
        if (slot < 0) {
            if (warn.get() && now - lastWarn > 200) { lastWarn = now; CrystalClient.LOG.warn("Auto Totem: no totems available"); }
            return;
        }
        busy = true;
        if (pending && now >= verifyUntil) {          // server never confirmed the last swap
            pending = false;
            if (++fails >= 5) {
                fails = 0; coolUntil = now + 40;
                CrystalClient.LOG.warn("Auto Totem: 5 failed swaps, cooling down for 2s");
            }
        }
        if (pending || now < coolUntil || now - lastAction < delay.i()) return;

        ItemStack off = p.getOffHandStack();
        if (!off.isEmpty() && prevItem == null) prevItem = off.getItem();
        if (!isTotem(p.getInventory().getStack(slot))) return;   // inventory changed under us; re-search next tick
        if (InventoryUtil.swapOffhand(slot)) {
            lastAction = now; pending = true;
            verifyUntil = now + 2 + InventoryUtil.latencyTicks();
            CrystalClient.debug("Auto Totem: swapped slot {} to offhand", slot);
        } else {
            CrystalClient.debug("Auto Totem: swap blocked (container open?)");
        }
    }

    private void handleRestore(ClientPlayerEntity p, boolean threatened, long now) {
        if (!restore.get() || prevItem == null || !mode.is("Danger")) { if (!restore.get()) prevItem = null; return; }
        float hp = p.getHealth() + p.getAbsorptionAmount();
        if (threatened || hp <= health.f() + 4) { safeSince = -1; return; }
        if (safeSince < 0) safeSince = now;
        if (now - safeSince < restoreDelay.i() || !PriorityManager.isFree()) return;
        final Item want = prevItem;
        int slot = InventoryUtil.find(s -> s.isOf(want), true);
        if (slot >= 0 && InventoryUtil.swapOffhand(slot)) { CrystalClient.debug("Auto Totem: restored {}", want); }
        prevItem = null; safeSince = -1;
    }

    private boolean threatened(ClientPlayerEntity p) {
        float hp = p.getHealth() + p.getAbsorptionAmount();
        if (hp <= health.f()) return true;
        if (!danger.get()) return false;
        double r = radius.d();
        for (EndCrystalEntity c : MinecraftClient.getInstance().world.getEntitiesByClass(EndCrystalEntity.class, p.getBoundingBox().expand(r), e -> e.isAlive())) {
            if (DamageCalc.crystal(c.getEntityPos(), p, true) >= hp - margin.f()) return true;
        }
        if (!p.isOnGround() && !p.getAbilities().flying && !p.isGliding() && p.fallDistance - 3 >= hp - margin.f()) return true;
        return false;
    }
}
