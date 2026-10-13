package dev.crystalclient.modules;

import dev.crystalclient.CrystalClient;
import dev.crystalclient.core.*;
import dev.crystalclient.core.Module;
import dev.crystalclient.setting.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import java.util.*;

public class CrystalAssist extends Module {
    private static boolean isCrystal(ItemStack s) { return s.isOf(Items.END_CRYSTAL); }
    private static boolean isObsidian(ItemStack s) { return s.isOf(Items.OBSIDIAN); }

    private final Setting.Choice playstyle = choice("Playstyle", "Applies a preset to the sliders below when changed", "Balanced", "Custom", "Aggressive", "Balanced", "Safe");
    private final Setting.Bool place = bool("Place crystals", "Assist crystal placement", true);
    private final Setting.Bool brk = bool("Break crystals", "Assist crystal breaking", true);
    private final Setting.Num placeDelay = num("Place delay (ticks)", "", 2, 0, 10, 1);
    private final Setting.Num breakDelay = num("Break delay (ticks)", "", 1, 0, 10, 1);
    private final Setting.Num placeRange = num("Place range", "", 4.5, 2, 6, 0.1);
    private final Setting.Num breakRange = num("Break range", "", 4.5, 2, 6, 0.1);
    private final Setting.Num minDamage = num("Min target damage", "Ignore spots/crystals below this", 6, 0, 20, 0.5);
    private final Setting.Num faceplaceHp = num("Face-place HP", "Lower the damage floor when target HP is at/below this", 8, 0, 20, 0.5);
    private final Setting.Num maxSelf = num("Max self damage", "Never act above this self damage", 6, 0, 20, 0.5);
    private final Setting.Num safetyMargin = num("Safety margin", "HP that must remain after self damage", 4, 0, 20, 0.5);
    private final Setting.Num selfWeight = num("Self damage weight", "Score = target dmg - weight * self dmg", 0.5, 0, 2, 0.1);
    private final Setting.Bool autoSwitch = bool("Auto switch", "Equip crystals via Auto Swap logic", true);
    private final Setting.Bool autoObsidian = bool("Auto obsidian", "Place obsidian near the target when no base exists", false);
    private final Setting.Bool defensive = bool("Defensive break", "Break crystals that would hurt you (Defensive tier)", true);
    private final Setting.Num defensiveMin = num("Defensive min damage", "Self damage that triggers defensive breaks", 4, 1, 20, 0.5);

    private long lastPlace = -100, lastBreak = -100;
    private String appliedStyle = "";
    public volatile float lastTargetDmg, lastSelfDmg;

    public CrystalAssist() { super("Crystal Assist", "Crystal placement/breaking assistance", Category.COMBAT, 2, true); }

    private void applyPlaystyle() {
        String s = playstyle.get();
        if (s.equals(appliedStyle)) return;
        appliedStyle = s;
        switch (s) {
            case "Aggressive" -> preset(0, 0, 4, 10, 2, 0.3, 10);
            case "Balanced" -> preset(2, 1, 6, 6, 4, 0.5, 8);
            case "Safe" -> preset(3, 2, 8, 3, 8, 1.0, 6);
            default -> {}
        }
    }

    private void preset(double pd, double bd, double min, double self, double margin, double weight, double face) {
        placeDelay.set(pd); breakDelay.set(bd); minDamage.set(min); maxSelf.set(self);
        safetyMargin.set(margin); selfWeight.set(weight); faceplaceHp.set(face);
    }

    @Override
    protected void onTick(MinecraftClient mc) {
        applyPlaystyle();
        ClientPlayerEntity p = mc.player;
        if (mc.currentScreen != null || AutoTotem.busy()) return;
        float hp = p.getHealth() + p.getAbsorptionAmount();
        long now = PriorityManager.now();
        PlayerEntity t = TargetSelector.get();

        if ((brk.get() || defensive.get()) && now - lastBreak >= breakDelay.i() && doBreak(mc, p, t, hp, now)) return;
        if (place.get() && t != null && now - lastPlace >= placeDelay.i()) doPlace(mc, p, t, hp, now);
    }

    private boolean accept(float tDmg, float sDmg, float hp, PlayerEntity t) {
        if (sDmg > maxSelf.f() || hp - sDmg <= safetyMargin.f()) return false;
        float thp = t.getHealth() + t.getAbsorptionAmount();
        if (tDmg >= thp) return true;                                     // kill shot
        float floor = thp <= faceplaceHp.d() ? 1.5f : minDamage.f();
        return tDmg >= floor;
    }

    // ---------------- breaking ----------------
    private boolean doBreak(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity t, float hp, long now) {
        double r = breakRange.d();
        EndCrystalEntity best = null; float bestScore = -1e9f; boolean bestDefensive = false;
        for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class, p.getBoundingBox().expand(r + 1), e -> e.isAlive())) {
            if (p.getEyePos().squaredDistanceTo(c.getEntityPos()) > r * r) continue;
            Vec3d pos = c.getEntityPos();
            float sDmg = DamageCalc.crystal(pos, p, true);
            float tDmg = t == null ? 0f : DamageCalc.crystal(pos, t, false);
            boolean offensive = brk.get() && t != null && accept(tDmg, sDmg, hp, t);
            boolean def = defensive.get() && sDmg >= defensiveMin.f();
            if (!offensive && !def) continue;
            float score = tDmg - sDmg * selfWeight.f() + (def ? 100 : 0);
            if (score > bestScore) { bestScore = score; best = c; bestDefensive = def && !offensive; lastTargetDmg = tDmg; lastSelfDmg = sDmg; }
        }
        if (best == null) return false;
        int lvl = bestDefensive ? Math.min(1, level()) : level();
        if (!PriorityManager.acquire(this, lvl, 1)) return false;
        mc.interactionManager.attackEntity(p, best);
        p.swingHand(Hand.MAIN_HAND);
        lastBreak = now;
        AutoSwap.noteActivity();
        CrystalClient.debug("Crystal Assist: broke crystal (target {} / self {})", lastTargetDmg, lastSelfDmg);
        return true;
    }

    // ---------------- placing ----------------
    private void doPlace(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity t, float hp, long now) {
        BlockPos best = findPlacement(mc, p, t, hp);
        if (best == null) {
            if (autoObsidian.get()) placeObsidian(mc, p, t, now);
            return;
        }
        Hand hand = autoSwitch.get() ? AutoSwap.hold(this, CrystalAssist::isCrystal)
                : (isCrystal(p.getMainHandStack()) ? Hand.MAIN_HAND : isCrystal(p.getOffHandStack()) ? Hand.OFF_HAND : null);
        if (hand == null || !PriorityManager.acquire(this, level(), 1)) return;
        if (BlockPlacer.use(best, net.minecraft.util.math.Direction.UP, hand)) {
            lastPlace = now;
            AutoSwap.noteActivity();
            CrystalClient.debug("Crystal Assist: placed at {} (target {} / self {})", best, lastTargetDmg, lastSelfDmg);
        }
    }

    private BlockPos findPlacement(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity t, float hp) {
        Vec3d eye = p.getEyePos();
        double r = placeRange.d();
        BlockPos tp = t.getBlockPos();
        List<BlockPos> cands = new ArrayList<>();
        for (int dx = -5; dx <= 5; dx++) for (int dy = -4; dy <= 2; dy++) for (int dz = -5; dz <= 5; dz++) {
            BlockPos b = tp.add(dx, dy, dz);
            BlockState s = mc.world.getBlockState(b);
            if (!s.isOf(Blocks.OBSIDIAN) && !s.isOf(Blocks.BEDROCK)) continue;
            if (!mc.world.getBlockState(b.up()).isAir() || !mc.world.getBlockState(b.up(2)).isAir()) continue;
            if (eye.squaredDistanceTo(Vec3d.ofCenter(b)) > r * r) continue;
            Vec3d c = new Vec3d(b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5);
            if (c.squaredDistanceTo(t.getEntityPos()) > 36) continue;       // out of explosion radius
            cands.add(b);
        }
        cands.sort(Comparator.comparingDouble(b -> new Vec3d(b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5).squaredDistanceTo(t.getEntityPos())));
        BlockPos best = null; float bestScore = -1e9f;
        int evaluated = 0;
        for (BlockPos b : cands) {
            if (evaluated++ >= 14) break;                                    // cap raycast cost per search
            Box space = new Box(b.getX(), b.getY() + 1, b.getZ(), b.getX() + 1, b.getY() + 3, b.getZ() + 1);
            if (!mc.world.getOtherEntities(null, space, e -> !e.isSpectator()).isEmpty()) continue;
            Vec3d c = new Vec3d(b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5);
            float sDmg = DamageCalc.crystal(c, p, true), tDmg = DamageCalc.crystal(c, t, false);
            if (!accept(tDmg, sDmg, hp, t)) continue;
            float score = tDmg - sDmg * selfWeight.f();
            if (score > bestScore) { bestScore = score; best = b; lastTargetDmg = tDmg; lastSelfDmg = sDmg; }
        }
        return best;
    }

    private void placeObsidian(MinecraftClient mc, ClientPlayerEntity p, PlayerEntity t, long now) {
        if (now - lastPlace < Math.max(3, placeDelay.i())) return;
        BlockPos base = t.getBlockPos().down(), pick = null;
        double bestD = 1e9, r = placeRange.d();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos b = base.add(dx, 0, dz);
            if (!mc.world.getBlockState(b).isAir() || !mc.world.getBlockState(b.up()).isAir() || !BlockPlacer.hasSupport(b)) continue;
            if (p.getEyePos().squaredDistanceTo(Vec3d.ofCenter(b)) > r * r) continue;
            double d = Vec3d.ofCenter(b).squaredDistanceTo(t.getEntityPos());
            if (d < bestD) { bestD = d; pick = b; }
        }
        if (pick == null) return;
        Hand hand = AutoSwap.hold(this, CrystalAssist::isObsidian);
        if (hand == null || !PriorityManager.acquire(this, level(), 1)) return;
        if (BlockPlacer.place(pick, hand, r)) { lastPlace = now; AutoSwap.noteActivity(); CrystalClient.debug("Crystal Assist: placed obsidian at {}", pick); }
    }
}
