package dev.crystalclient.core;

import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class BlockPlacer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private BlockPlacer() {}

    public static boolean hasSupport(BlockPos pos) {
        for (Direction d : Direction.values()) if (supports(pos.offset(d))) return true;
        return false;
    }

    private static boolean supports(BlockPos n) {
        BlockState s = mc.world.getBlockState(n);
        return !s.isReplaceable() && !s.hasBlockEntity() && !(s.getBlock() instanceof RespawnAnchorBlock);
    }

    /** Places whatever is in {@code hand} at {@code pos} by clicking an adjacent solid face. */
    public static boolean place(BlockPos pos, Hand hand, double range) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (!supports(n)) continue;
            Direction face = d.getOpposite();
            Vec3d hit = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (mc.player.getEyePos().squaredDistanceTo(hit) > range * range) continue;
            ActionResult r = mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(hit, face, n, false));
            if (r.isAccepted()) { mc.player.swingHand(hand); return true; }
        }
        return false;
    }

    /** Right-clicks a specific block (used for charging/detonating anchors, placing crystals on top faces). */
    public static boolean use(BlockPos pos, Direction face, Hand hand) {
        Vec3d hit = Vec3d.ofCenter(pos).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
        ActionResult r = mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(hit, face, pos, false));
        if (r.isAccepted()) mc.player.swingHand(hand);
        return r.isAccepted();
    }
}
