package dev.crystalclient.modules;

import dev.crystalclient.core.*;
import dev.crystalclient.core.Module;
import dev.crystalclient.setting.Setting;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Test-world helper: when you place a rail, a TNT minecart is placed on it from your hotbar.
 * Only acts on rails you just placed yourself, and only while the module is ON.
 */
public class RailHelper extends Module {
    private static boolean isTntMinecart(ItemStack s) { return s.isOf(Items.TNT_MINECART); }

    private final Setting.Num range = num("Place range", "Max distance to the rail", 4.5, 2, 6, 0.1);
    private final Setting.Num cooldown = num("Cooldown (ticks)", "Minimum ticks between carts", 4, 0, 20, 1);
    private final Setting.Bool autoSwitch = bool("Auto switch", "Equip the TNT minecart automatically", true);

    private BlockPos lastRail;
    private long lastPlaced = -100;

    public RailHelper() { super("Rail Helper", "Places a TNT minecart on rails you place (test worlds)", Category.COMBAT, 3, true); }

    @Override
    protected void onTick(MinecraftClient mc) {
        if (mc.currentScreen != null || mc.world == null || mc.player == null) return;
        long now = PriorityManager.now();

        // Detect a freshly placed rail: a rail block that wasn't there last tick, right where we're looking.
        if (!(mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)) return;
        BlockPos rail = hit.getBlockPos().offset(hit.getSide());
        var state = mc.world.getBlockState(rail);
        if (!state.isOf(Blocks.RAIL) && !state.isOf(Blocks.POWERED_RAIL) && !state.isOf(Blocks.DETECTOR_RAIL) && !state.isOf(Blocks.ACTIVATOR_RAIL)) return;
        if (rail.equals(lastRail)) return;
        lastRail = rail;

        if (now - lastPlaced < cooldown.i()) return;
        if (mc.world.getOtherEntities(null, new net.minecraft.util.math.Box(rail).expand(0.5), e -> e instanceof TntMinecartEntity).size() > 0) return;

        Vec3d eye = mc.player.getEyePos();
        if (eye.squaredDistanceTo(Vec3d.ofCenter(rail)) > range.d() * range.d()) return;

        Hand hand = autoSwitch.get() ? AutoSwap.hold(this, RailHelper::isTntMinecart)
                : (isTntMinecart(mc.player.getMainHandStack()) ? Hand.MAIN_HAND : isTntMinecart(mc.player.getOffHandStack()) ? Hand.OFF_HAND : null);
        if (hand == null || !PriorityManager.acquire(this, level(), 1)) return;

        // Right-click the rail's top face to place the minecart on it.
        var result = mc.interactionManager.interactBlock(mc.player, hand,
                new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(rail).add(0, 0.5, 0), net.minecraft.util.math.Direction.UP, rail, false));
        if (result.isAccepted()) {
            mc.player.swingHand(hand);
            lastPlaced = now;
            AutoSwap.noteActivity();
            CrystalClient.debug("Rail Helper: placed TNT minecart at {}", rail);
        }
    }
}
