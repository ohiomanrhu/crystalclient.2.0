package dev.crystalclient.modules;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Vanilla explosion damage model (exposure raycasts, difficulty, armor, resistance, assumed blast protection). */
public final class DamageCalc {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static final float CRYSTAL_POWER = 6f, ANCHOR_POWER = 5f;
    private DamageCalc() {}

    public static float crystal(Vec3d center, LivingEntity e, boolean self) { return explosion(center, e, CRYSTAL_POWER, self); }
    public static float anchor(BlockPos pos, LivingEntity e, boolean self) { return explosion(Vec3d.ofCenter(pos), e, ANCHOR_POWER, self); }

    public static float explosion(Vec3d c, LivingEntity e, float power, boolean self) {
        double q = power * 2.0;
        double dist = Math.sqrt(e.squaredDistanceTo(c)) / q;
        if (dist > 1.0) return 0f;
        double impact = (1.0 - dist) * exposure(c, e);
        float dmg = (float) ((impact * impact + impact) / 2.0 * 7.0 * q + 1.0);

        switch (mc.world.getDifficulty()) {
            case PEACEFUL -> { return 0f; }
            case EASY -> dmg = Math.min(dmg / 2f + 1f, dmg);
            case HARD -> dmg *= 1.5f;
            default -> {}
        }
        float armor = e.getArmor();
        float tough = (float) e.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        float g = MathHelper.clamp(armor - dmg / (2f + tough / 4f), armor * 0.2f, 20f);
        dmg *= 1f - g / 25f;

        StatusEffectInstance res = e.getStatusEffect(StatusEffects.RESISTANCE);
        if (res != null) dmg *= Math.max(0f, 1f - (res.getAmplifier() + 1) * 0.2f);

        CoreSettings cs = CoreSettings.I;
        float epf = cs == null ? 0f : (self ? cs.selfProt.f() : cs.targetProt.f());
        dmg *= 1f - Math.min(20f, epf) / 25f;
        return Math.max(dmg, 0f);
    }

    /** Fraction of sample points on the entity's hitbox with a clear line to the explosion center. */
    public static float exposure(Vec3d src, Entity e) {
        Box b = e.getBoundingBox();
        double dx = 1.0 / ((b.maxX - b.minX) * 2.0 + 1.0), dy = 1.0 / ((b.maxY - b.minY) * 2.0 + 1.0), dz = 1.0 / ((b.maxZ - b.minZ) * 2.0 + 1.0);
        double ox = (1.0 - Math.floor(1.0 / dx) * dx) / 2.0, oz = (1.0 - Math.floor(1.0 / dz) * dz) / 2.0;
        if (dx < 0 || dy < 0 || dz < 0) return 0f;
        int hit = 0, total = 0;
        for (double x = 0; x <= 1.0; x += dx)
            for (double y = 0; y <= 1.0; y += dy)
                for (double z = 0; z <= 1.0; z += dz) {
                    Vec3d p = new Vec3d(MathHelper.lerp(x, b.minX, b.maxX) + ox, MathHelper.lerp(y, b.minY, b.maxY), MathHelper.lerp(z, b.minZ, b.maxZ) + oz);
                    if (mc.world.raycast(new RaycastContext(p, src, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e)).getType() == HitResult.Type.MISS) hit++;
                    total++;
                }
        return total == 0 ? 0f : (float) hit / total;
    }
}
