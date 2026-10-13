package dev.crystalclient.modules;

import dev.crystalclient.CrystalClient;
import dev.crystalclient.core.*;
import dev.crystalclient.core.Module;
import dev.crystalclient.setting.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

import java.util.*;

/** Place -> charge -> detonate state machine. Anchors only explode outside the Nether. */
public class AutoAnchor extends Module {
    private static boolean isAnchor(ItemStack s) { return s.isOf(Items.RESPAWN_ANCHOR); }
    private static boolean isGlow(ItemStack s) { return s.isOf(Items.GLOWSTONE); }

    private final Setting.Num placeRange = num("Place range", "", 4.5, 2, 6, 0.1);
    private final Setting.Num minDamage = num("Min target damage", "Don't detonate below this", 8, 0, 20, 0.5);
    private final Setting.Num maxSelf = num("Max self damage", "", 4, 0, 20, 0.5);
    private final Setting.Num safetyMargin = num("Safety margin", "HP that must remain after self damage", 6, 0, 20, 0.5);
    private final Setting.Num charges = num("Glowstone charges", "Charges applied before detonation (1-4)", 1, 1, 4, 1);
    private final Setting.Num placeDelay = num("Place delay (ticks)", "", 2, 0, 10, 1);
    private final Setting.Num chargeDelay = num("Charge delay (ticks)", "", 1, 0, 10, 1);
    private final Setting.Num detonateDelay = num("Detonate delay (ticks)", "", 1, 0, 10, 1);
    private final Setting.Bool autoSwitch = bool("Auto switch", "Equip anchors/glowstone/detonator automatically", true);

    private long lastPlace = -100, lastCharge = -100, lastDet = -100;
    private boolean warnedDim;

    public AutoAnchor() { super("Auto Anchor", "Automatic anchor place, charge and detonate", Category.COMBAT, 2, true); }

    @Override protected void onEnable() { warnedDim = false; }

    @Override
    protected void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (mc.world.getRegistryKey() == World.NETHER) {            // anchors do not explode here
            if (!warnedDim) { warnedDim = true; CrystalClient.LOG.warn("Auto Anchor: idle in the Nether (anchors do not explode)"); }
            return;
        }
        warnedDim = false;
        PlayerEntity t = TargetSelector.get();
        if (t == null || mc.currentScreen != null || AutoTotem.busy()) return;
        float hp = p.getHealth() + p.getAbsorptionAmount();
        long now = PriorityManager.now();

        BlockPos anchor = findExisting(mc, p, t);
        if (anchor == null) {
            if (now - lastPlace < placeDelay.i()) return;
            BlockPos spot = bestPlacement(mc, p, t, hp);
            if (spot == null) return;
            Hand h = equip(AutoAnchor::isAnchor);
            if (h == null || !PriorityManager.acquire(this, level(), 1)) return;
            if (BlockPlacer.place(spot, h, placeRange.d())) { lastPlace = now; AutoSwap.noteActivity(); CrystalClient.debug("Auto Anchor: placed at {}", spot); }
            return;
        }

        BlockState st = mc.world.getBlockState(anchor);
        int ch = st.get(RespawnAnchorBlock.CHARGES);
        float sDmg = DamageCalc.anchor(anchor, p, true), tDmg = DamageCalc.anchor(anchor, t, false);
        boolean safe = sDmg <= maxSelf.f() && hp - sDmg > safetyMargin.f();
        if (!safe) { CrystalClient.debug("Auto Anchor: self damage {} too high, holding", sDmg); return; }

        if (ch < charges.i()) {
            if (now - lastCharge < chargeDelay.i()) return;
            Hand h = equip(AutoAnchor::isGlow);
            if (h == null || !PriorityManager.acquire(this, level(), 1)) return;
            if (BlockPlacer.use(anchor, Direction.UP, h)) { lastCharge = now; AutoSwap.noteActivity(); }
            return;
        }

        float thp = t.getHealth() + t.getAbsorptionAmount();
        if (tDmg < minDamage.f() && tDmg < thp) return;
        if (now - lastDet < detonateDelay.i()) return;
        Hand h = holdDetonator(p);
        if (h == null || !PriorityManager.acquire(this, level(), 1)) return;
        if (BlockPlacer.use(anchor, Direction.UP, h)) {
            lastDet = now; AutoSwap.noteActivity();
            CrystalClient.debug("Auto Anchor: detonated (target {} / self {})", tDmg, sDmg);
        }
    }

    private Hand equip(java.util.function.Predicate<ItemStack> p) {
        if (autoSwitch.get()) return AutoSwap.hold(this, p);
        var pl = mc.player;
        return p.test(pl.getMainHandStack()) ? Hand.MAIN_HAND : p.test(pl.getOffHandStack()) ? Hand.OFF_HAND : null;
    }

    /** Anything that is not glowstone detonates a charged anchor (an empty hand or a totem is ideal). */
    private Hand holdDetonator(ClientPlayerEntity p) {
        if (!isGlow(p.getMainHandStack())) return Hand.MAIN_HAND;
        if (!autoSwitch.get()) return null;
        int slot = InventoryUtil.findHotbar(s -> !isGlow(s) && !isAnchor(s));
        if (slot < 0) slot = InventoryUtil.findHotbar(s -> !isGlow(s));
        if (slot < 0 || !PriorityManager.acquire(this, level(), 2)) return null;
        InventoryUtil.select(slot);
        return Hand.MAIN_HAND;
    }

    private BlockPos findExisting(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity t) {
        BlockPos tp = t.getBlockPos(), best = null; double bestD = 1e9, r = placeRange.d();
        for (int dx = -4; dx <= 4; dx++) for (int dy = -3; dy <= 3; dy++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos b = tp.add(dx, dy, dz);
            if (!(mc.world.getBlockState(b).getBlock() instanceof RespawnAnchorBlock)) continue;
            double d = p.getEyePos().squaredDistanceTo(Vec3d.ofCenter(b));
            if (d <= r * r && d < bestD) { bestD = d; best = b; }
        }
        return best;
    }

    private BlockPos bestPlacement(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity t, float hp) {
        BlockPos tp = t.getBlockPos();
        double r = placeRange.d();
        List<BlockPos> cands = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos b = tp.add(dx, dy, dz);
            if (!mc.world.getBlockState(b).isAir() || !BlockPlacer.hasSupport(b)) continue;
            if (p.getEyePos().squaredDistanceTo(Vec3d.ofCenter(b)) > r * r) continue;
            if (!mc.world.getOtherEntities(null, new Box(b), e -> e.isAlive() && !e.isSpectator()).isEmpty()) continue;
            cands.add(b);
        }
        cands.sort(Comparator.comparingDouble(b -> Vec3d.ofCenter(b).squaredDistanceTo(t.getEntityPos())));
        BlockPos best = null; float bestScore = -1e9f; int n = 0;
        for (BlockPos b : cands) {
            if (n++ >= 10) break;
            float sDmg = DamageCalc.anchor(b, p, true), tDmg = DamageCalc.anchor(b, t, false);
            if (sDmg > maxSelf.f() || hp - sDmg <= safetyMargin.f()) continue;
            if (tDmg < minDamage.f() && tDmg < t.getHealth() + t.getAbsorptionAmount()) continue;
            float score = tDmg - sDmg * 0.5f;
            if (score > bestScore) { bestScore = score; best = b; }
        }
        return best;
    }
}
